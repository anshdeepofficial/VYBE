package com.theveloper.pixelplay.data.network.lyrics

import com.theveloper.pixelplay.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Secondary plain-lyrics source used only when local lyrics and LRCLIB miss. */
@Singleton
class LyricsOvhProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun find(song: Song): String? = withContext(Dispatchers.IO) {
        val artist = song.displayArtist.substringBefore(" feat.", missingDelimiterValue = song.displayArtist).trim()
        val title = song.title.substringBefore(" feat.", missingDelimiterValue = song.title).trim()
        if (artist.isBlank() || title.isBlank()) return@withContext null

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.lyrics.ovh")
            .addPathSegment("v1")
            .addPathSegment(artist)
            .addPathSegment(title)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                JSONObject(response.body?.string().orEmpty())
                    .optString("lyrics")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}
