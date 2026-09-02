package com.theveloper.pixelplay.data.recognition

import android.net.Uri
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeMusicEngine
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

data class ReelRecognitionResult(
    val song: Song,
    val matchedOffsetSeconds: Float? = null,
    val sourceUrl: String,
    val sourcePlatform: String,
    val originalAudioTitle: String? = null,
)

@Singleton
class SocialReelAudioRecognizer @Inject constructor(
    private val httpClient: OkHttpClient,
    private val onlineMusicRepository: OnlineMusicRepository,
    private val youTubeEngine: YouTubeMusicEngine,
) {

    fun isSocialReelUrl(text: String): Boolean {
        val trimmed = text.trim()
        val url = extractUrl(trimmed) ?: return false
        val lower = url.lowercase()
        return lower.contains("instagram.com/reel") ||
            lower.contains("instagram.com/reels") ||
            lower.contains("instagram.com/p/") ||
            lower.contains("instagr.am/p/") ||
            lower.contains("instagram.com/audio") ||
            lower.contains("youtube.com/shorts") ||
            lower.contains("youtu.be/") ||
            lower.contains("youtube.com/watch")
    }

    suspend fun recognizeFromUrl(rawText: String): ReelRecognitionResult? = withContext(Dispatchers.IO) {
        val url = extractUrl(rawText) ?: return@withContext null
        val lower = url.lowercase()

        when {
            lower.contains("instagram.com") || lower.contains("instagr.am") -> {
                recognizeInstagramReel(url)
            }
            lower.contains("youtube.com") || lower.contains("youtu.be") -> {
                recognizeYouTubeShortOrVideo(url)
            }
            else -> null
        }
    }

    private suspend fun recognizeInstagramReel(url: String): ReelRecognitionResult? {
        val shortcode = extractInstagramShortcode(url) ?: return null
        val embedUrl = "https://www.instagram.com/reel/$shortcode/embed/captioned/"

        val embedRequest = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        var audioTitle: String? = null
        val matchedOffset: Float? = null

        runCatching {
            httpClient.newCall(embedRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    // 1. Check for CaptionAudioText (e.g. <span class="CaptionAudioText">Artist • Song</span>)
                    val audioMatcher = Pattern.compile("class=[\"']CaptionAudioText[\"'][^>]*>([^<]+)<", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (audioMatcher.find()) {
                        audioTitle = audioMatcher.group(1)?.trim()
                    }

                    // 2. Check for OG Title or Description
                    if (audioTitle.isNullOrBlank()) {
                        val ogTitleMatcher = Pattern.compile("property=[\"']og:title[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                        if (ogTitleMatcher.find()) {
                            val candidate = ogTitleMatcher.group(1)?.trim()
                            if (!candidate.isNullOrBlank() && !candidate.contains("Instagram", ignoreCase = true)) {
                                audioTitle = candidate
                            }
                        }
                    }

                    // 3. Check for JSON data in HTML
                    if (audioTitle.isNullOrBlank()) {
                        val jsonMatcher = Pattern.compile("window\\.__additionalDataLoaded\\s*\\(\\s*['\"][^'\"]+['\"]\\s*,\\s*(\\{.+?\\})\\s*\\);", Pattern.DOTALL).matcher(html)
                        if (jsonMatcher.find()) {
                            val jsonString = jsonMatcher.group(1)
                            val obj = runCatching { JSONObject(jsonString) }.getOrNull()
                            val sound = obj?.optJSONObject("graphql")?.optJSONObject("shortcode_media")?.optJSONObject("clips_music_attribution_info")
                            if (sound != null) {
                                val songName = sound.optString("song_name")
                                val artistName = sound.optString("artist_name")
                                if (songName.isNotBlank()) {
                                    audioTitle = listOf(artistName, songName).filter(String::isNotBlank).joinToString(" - ")
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure { Timber.w(it, "Failed to load Instagram embed for %s", shortcode) }

        val cleanQuery = cleanAudioQuery(audioTitle)
        if (cleanQuery.isBlank()) {
            return null
        }

        // Search YouTube Music for the resolved audio track
        val searchResult = runCatching {
            onlineMusicRepository.searchMusicStructured(cleanQuery, "IN")
        }.getOrNull()

        val matchedSong = searchResult?.songs?.firstOrNull() ?: searchResult?.videos?.firstOrNull() ?: return null

        return ReelRecognitionResult(
            song = matchedSong,
            matchedOffsetSeconds = matchedOffset,
            sourceUrl = url,
            sourcePlatform = "Instagram",
            originalAudioTitle = audioTitle,
        )
    }

    private suspend fun recognizeYouTubeShortOrVideo(url: String): ReelRecognitionResult? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()

        val videoId = when {
            host.contains("youtu.be") -> uri.lastPathSegment?.trim()
            host.contains("youtube.com") && uri.path?.startsWith("/shorts/") == true -> uri.lastPathSegment?.trim()
            host.contains("youtube.com") -> uri.getQueryParameter("v")?.trim()
            else -> null
        }?.takeIf(String::isNotBlank)?.take(64) ?: return null

        // Parse timestamp parameter if available (e.g. ?t=70 or ?t=1m10s)
        val timeParam = uri.getQueryParameter("t") ?: uri.getQueryParameter("time_continue")
        val parsedOffset = timeParam?.let(::parseTimestampSeconds)

        val track = runCatching { onlineMusicRepository.getTrackDetails(videoId) }.getOrNull()
            ?: runCatching { youTubeEngine.getTrackDetails(videoId)?.toSong() }.getOrNull()
            ?: return null

        return ReelRecognitionResult(
            song = track,
            matchedOffsetSeconds = parsedOffset,
            sourceUrl = url,
            sourcePlatform = "YouTube",
            originalAudioTitle = "${track.artist} - ${track.title}",
        )
    }

    private fun extractInstagramShortcode(url: String): String? {
        val pattern = Pattern.compile("instagram\\.com/(?:reel|reels|p|audio)/([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractUrl(text: String): String? {
        val matcher = Pattern.compile("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\.&]+", Pattern.CASE_INSENSITIVE).matcher(text)
        return if (matcher.find()) matcher.group(0) else null
    }

    private fun cleanAudioQuery(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace("Original audio", "", ignoreCase = true)
            .replace("Original Sound", "", ignoreCase = true)
            .replace("Audio", "", ignoreCase = true)
            .replace("•", " ")
            .replace("-", " ")
            .replace(Regex("""[^\w\s\u0A00-\u0A7F\u0900-\u097F]"""), " ")
            .trim()
    }

    private fun parseTimestampSeconds(param: String): Float? {
        val clean = param.trim().lowercase()
        return runCatching {
            if (clean.endsWith("s") && !clean.contains("m") && !clean.contains("h")) {
                clean.removeSuffix("s").toFloatOrNull()
            } else if (clean.contains("m") || clean.contains("h")) {
                var total = 0f
                var remaining = clean
                if (remaining.contains("h")) {
                    val h = remaining.substringBefore("h").toFloatOrNull() ?: 0f
                    total += h * 3600f
                    remaining = remaining.substringAfter("h")
                }
                if (remaining.contains("m")) {
                    val m = remaining.substringBefore("m").toFloatOrNull() ?: 0f
                    total += m * 60f
                    remaining = remaining.substringAfter("m")
                }
                if (remaining.contains("s")) {
                    val s = remaining.substringBefore("s").toFloatOrNull() ?: 0f
                    total += s
                } else if (remaining.isNotBlank()) {
                    total += remaining.toFloatOrNull() ?: 0f
                }
                total
            } else {
                clean.toFloatOrNull()
            }
        }.getOrNull()?.takeIf { it > 0f }
    }
}