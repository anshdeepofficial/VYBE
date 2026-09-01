package com.theveloper.pixelplay.data.recognition

import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Network counterpart of Echo Music's Shazam-signature recognition flow. */
class ShazamRecognitionProvider @Inject constructor(
    private val client: OkHttpClient,
) : RecognitionProvider {
    override suspend fun recognize(fingerprint: String, durationSeconds: Int): RecognitionMetadata? =
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis() / 1_000
            val body = JSONObject()
                .put("geolocation", JSONObject().put("altitude", 0.0).put("latitude", 0.0).put("longitude", 0.0))
                .put("signature", JSONObject().put("samplems", durationSeconds * 1_000L).put("timestamp", timestamp).put("uri", fingerprint))
                .put("timestamp", timestamp)
                .put("timezone", "Asia/Kolkata")
            val url = "https://amp.shazam.com/discovery/v5/en/US/android/-/tag/${UUID.randomUUID().toString().uppercase()}/${UUID.randomUUID()}" +
                "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 13; VYBE)")
                .header("Content-Language", "en_US")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> return@withContext null
                    response.code == 429 -> throw RateLimitedException()
                    !response.isSuccessful -> throw RecognitionHttpException(response.code)
                }
                val root = response.body?.string()?.let(::JSONObject) ?: throw MalformedRecognitionException()
                val track = root.optJSONObject("track") ?: return@withContext null
                val title = track.optString("title").trim()
                val artist = track.optString("subtitle").trim()
                if (title.isBlank() || artist.isBlank()) return@withContext null
                val sections = track.optJSONArray("sections")
                var album: String? = null
                if (sections != null) for (index in 0 until sections.length()) {
                    val section = sections.optJSONObject(index) ?: continue
                    val metadata = section.optJSONArray("metadata") ?: continue
                    for (metadataIndex in 0 until metadata.length()) {
                        val item = metadata.optJSONObject(metadataIndex) ?: continue
                        if (item.optString("title").equals("Album", true)) album = item.optString("text").takeIf(String::isNotBlank)
                    }
                }
                val images = track.optJSONObject("images")
                RecognitionMetadata(
                    title = title,
                    artist = artist,
                    album = album,
                    artworkUrl = images?.optString("coverarthq")?.takeIf(String::isNotBlank)
                        ?: images?.optString("coverart")?.takeIf(String::isNotBlank),
                    isrc = track.optString("isrc").takeIf(String::isNotBlank),
                )
            }
        }
}
