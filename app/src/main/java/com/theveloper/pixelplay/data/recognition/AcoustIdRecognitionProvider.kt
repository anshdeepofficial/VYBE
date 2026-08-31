package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.BuildConfig
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AcoustIdRecognitionProvider : RecognitionProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    override suspend fun recognize(fingerprint: String, durationSeconds: Int): RecognitionMetadata? = withContext(Dispatchers.IO) {
        require(fingerprint.isNotBlank() && durationSeconds in 5..15)
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            try {
                val encoded = URLEncoder.encode(fingerprint, Charsets.UTF_8.name())
                val url = "https://api.acoustid.org/v2/lookup?client=${BuildConfig.ACOUSTID_CLIENT_KEY}" +
                    "&meta=recordings+releasegroups&duration=$durationSeconds&fingerprint=$encoded"
                val request = Request.Builder().url(url).header("Accept", "application/json").build()
                val payload = client.newCall(request).execute().use { response ->
                    when (response.code) {
                        429 -> throw RateLimitedException()
                        in 500..599 -> throw IOException("Recognition service unavailable")
                    }
                    if (!response.isSuccessful) throw RecognitionHttpException(response.code)
                    response.body.string()
                }
                val root = JSONObject(payload)
                if (root.optString("status") != "ok") throw MalformedRecognitionException()
                val results = root.optJSONArray("results") ?: return@withContext null
                var best: RecognitionMetadata? = null
                var bestScore = 0.0
                for (i in 0 until results.length()) {
                    val result = results.optJSONObject(i) ?: continue
                    val recordings = result.optJSONArray("recordings") ?: continue
                    for (j in 0 until recordings.length()) {
                        val recording = recordings.optJSONObject(j) ?: continue
                        val title = recording.optString("title").trim()
                        val artists = recording.optJSONArray("artists")
                        val artist = artists?.optJSONObject(0)?.optString("name")?.trim().orEmpty()
                        if (title.isBlank() || artist.isBlank()) continue
                        val groups = recording.optJSONArray("releasegroups")
                        val group = groups?.optJSONObject(0)
                        val score = result.optDouble("score", 0.0)
                        if (score > bestScore) {
                            val groupId = group?.optString("id")?.takeIf(String::isNotBlank)
                            bestScore = score
                            best = RecognitionMetadata(
                                title = title,
                                artist = artist,
                                album = group?.optString("title")?.takeIf(String::isNotBlank),
                                artworkUrl = groupId?.let { "https://coverartarchive.org/release-group/$it/front-500" },
                            )
                        }
                    }
                }
                return@withContext best?.takeIf { bestScore >= 0.35 }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                if (attempt == 0 && (error is IOException || error is RateLimitedException)) delay(900)
                else throw error
            }
        }
        throw lastError ?: IOException("Recognition failed")
    }
}

class RateLimitedException : IOException("Recognition is temporarily rate limited")
class RecognitionHttpException(val status: Int) : IOException("Recognition request failed ($status)")
class MalformedRecognitionException : IOException("Malformed recognition response")
