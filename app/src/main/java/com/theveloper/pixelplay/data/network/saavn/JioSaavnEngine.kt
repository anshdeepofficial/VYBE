package com.theveloper.pixelplay.data.network.saavn

import android.util.Log
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JioSaavnEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "JioSaavnEngine"
        // High-availability Saavn API base mirrors
        private val API_BASES = listOf(
            "https://saavn.dev/api",
            "https://jiosaavn-api-privateindexer.vercel.app/api",
            "https://saavn.me/api"
        )
    }

    private val streamCache = ConcurrentHashMap<String, String>()

    /**
     * Resolve direct playable 320kbps/160kbps CDN stream URL by song query or title + artist.
     */
    suspend fun resolveStreamByQuery(query: String): String? = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext null

        streamCache[cleanQuery]?.let { return@withContext it }

        fetchNativeSearchResults(cleanQuery, 3)
            ?.optJSONObject(0)
            ?.let(::nativeStreamUrl)
            ?.takeIf(String::isNotBlank)
            ?.let { streamUrl ->
                streamCache[cleanQuery] = streamUrl
                return@withContext streamUrl
            }

        for (apiBase in API_BASES) {
            val streamUrl = fetchStreamFromApi(apiBase, cleanQuery)
            if (!streamUrl.isNullOrBlank()) {
                streamCache[cleanQuery] = streamUrl
                return@withContext streamUrl
            }
        }
        null
    }

    private fun fetchStreamFromApi(apiBase: String, query: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$apiBase/search/songs?query=$encodedQuery&limit=3"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val bodyString = response.body?.string().orEmpty()
            if (bodyString.isBlank()) return null

            val json = JSONObject(bodyString)
            val success = json.optBoolean("success", false) || json.optString("status") == "SUCCESS"
            if (!success && !json.has("data")) return null

            val data = json.optJSONObject("data")
            val results = data?.optJSONArray("results") ?: json.optJSONArray("data") ?: return null

            if (results.length() == 0) return null

            val firstSong = results.getJSONObject(0)
            val downloadUrls = firstSong.optJSONArray("downloadUrl")
            if (downloadUrls != null && downloadUrls.length() > 0) {
                // Pick highest quality (320kbps or 160kbps)
                var bestUrl: String? = null
                for (i in downloadUrls.length() - 1 downTo 0) {
                    val entry = downloadUrls.optJSONObject(i)
                    val streamLink = entry?.optString("url", entry.optString("link", ""))
                    if (!streamLink.isNullOrBlank()) {
                        bestUrl = streamLink
                        break
                    }
                }
                return bestUrl
            }

            // Fallback for media_url
            val mediaUrl = firstSong.optString("media_url", firstSong.optString("url", ""))
            if (mediaUrl.isNotBlank() && (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://"))) {
                return mediaUrl
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "Saavn resolve failed on $apiBase: ${e.message}")
            null
        }
    }

    /**
     * Search songs from JioSaavn.
     */
    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val songs = mutableListOf<Song>()
        val nativeResults = fetchNativeSearchResults(cleanQuery, 15)
        nativeResults?.let { results ->
            for (index in 0 until results.length()) {
                results.optJSONObject(index)?.toNativeSong()?.let(songs::add)
            }
        }
        if (nativeResults != null) return@withContext songs

        for (apiBase in API_BASES) {
            try {
                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val url = "$apiBase/search/songs?query=$encodedQuery&limit=15"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    if (bodyString.isNotBlank()) {
                        val json = JSONObject(bodyString)
                        val data = json.optJSONObject("data")
                        val results = data?.optJSONArray("results") ?: json.optJSONArray("data")
                        if (results != null) {
                            for (i in 0 until results.length()) {
                                val item = results.getJSONObject(i)
                                val id = item.optString("id", "")
                                val name = item.optString("name", item.optString("title", ""))
                                val artist = item.optJSONObject("artists")?.optJSONArray("primary")?.let { arr ->
                                    if (arr.length() > 0) arr.getJSONObject(0).optString("name") else null
                                } ?: item.optString("primaryArtists", item.optString("artist", "Unknown"))
                                val album = item.optJSONObject("album")?.optString("name") ?: item.optString("album", "")
                                val duration = item.optInt("duration", 0) * 1000L

                                var artworkUrl: String? = null
                                val imageArray = item.optJSONArray("image")
                                if (imageArray != null && imageArray.length() > 0) {
                                    artworkUrl = imageArray.getJSONObject(imageArray.length() - 1).optString("url", imageArray.getJSONObject(imageArray.length() - 1).optString("link", ""))
                                }

                                var streamUrl = ""
                                val downloadUrls = item.optJSONArray("downloadUrl")
                                if (downloadUrls != null && downloadUrls.length() > 0) {
                                    for (j in downloadUrls.length() - 1 downTo 0) {
                                        val entry = downloadUrls.optJSONObject(j)
                                        val link = entry?.optString("url", entry.optString("link", ""))
                                        if (!link.isNullOrBlank()) {
                                            streamUrl = link
                                            break
                                        }
                                    }
                                }

                                if (id.isNotBlank() && name.isNotBlank()) {
                                    val finalPath = if (streamUrl.isNotBlank()) streamUrl else "saavn://$id"
                                    songs.add(
                                        Song(
                                            id = "saavn_$id",
                                            title = name,
                                            artist = artist,
                                            artistId = 0L,
                                            album = album,
                                            albumId = 0L,
                                            path = finalPath,
                                            contentUriString = finalPath,
                                            albumArtUriString = artworkUrl,
                                            duration = duration,
                                            mimeType = "audio/mp4",
                                            bitrate = 320,
                                            sampleRate = 44100
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (songs.isNotEmpty()) break
                }
            } catch (e: Exception) {
                Log.d(TAG, "Saavn search error on $apiBase: ${e.message}")
            }
        }
        songs
    }

    /** Uses JioSaavn's own web search endpoint, avoiding unreliable third-party mirrors. */
    private fun fetchNativeSearchResults(query: String, limit: Int): JSONArray? = try {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "https://www.jiosaavn.com/api.php" +
            "?__call=search.getResults&_format=json&_marker=0&ctx=web6dot0" +
            "&cc=in&q=$encodedQuery&p=1&n=$limit"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            JSONObject(response.body?.string().orEmpty()).optJSONArray("results")
        }
    } catch (error: Exception) {
        Log.d(TAG, "Native Saavn search failed: ${error.message}")
        null
    }

    private fun JSONObject.toNativeSong(): Song? {
        val id = optString("id").trim()
        val title = decodeEntities(optString("song", optString("title"))).trim()
        if (id.isBlank() || title.isBlank()) return null
        val artist = decodeEntities(optString("primary_artists", optString("singers", "Unknown")))
        val album = decodeEntities(optString("album"))
        val streamUrl = nativeStreamUrl(this).orEmpty()
        val artwork = optString("image")
            .replace("150x150", "500x500")
            .takeIf(String::isNotBlank)
        val finalPath = streamUrl.ifBlank { "saavn://$id" }
        return Song(
            id = "saavn_$id",
            title = title,
            artist = artist,
            artistId = 0L,
            album = album,
            albumId = 0L,
            path = finalPath,
            contentUriString = finalPath,
            albumArtUriString = artwork,
            duration = optLong("duration", 0L) * 1_000L,
            mimeType = "audio/mp4",
            bitrate = if (optString("320kbps").equals("true", ignoreCase = true)) 320 else 160,
            sampleRate = 44_100,
        )
    }

    private fun nativeStreamUrl(item: JSONObject): String? = runCatching {
        val encryptedUrl = item.optString("encrypted_media_url")
        if (encryptedUrl.isBlank()) return@runCatching null
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec("38346591".toByteArray(StandardCharsets.US_ASCII), "DES"),
        )
        val decrypted = String(
            cipher.doFinal(Base64.getDecoder().decode(encryptedUrl)),
            StandardCharsets.UTF_8,
        )
        if (item.optString("320kbps").equals("true", ignoreCase = true)) {
            decrypted.replace("_96.mp4", "_320.mp4")
        } else {
            decrypted.replace("_96.mp4", "_160.mp4")
        }
    }.getOrNull()

    private fun decodeEntities(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&amp;", "&")

    fun invalidateCache(query: String) {
        streamCache.remove(query.trim())
    }
}
