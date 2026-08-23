package com.theveloper.pixelplay.data.sponsorblock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

data class SponsorSegment(val startMs: Long, val endMs: Long, val category: String)

@Singleton
class SponsorBlockRepository @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun segments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        runCatching {
            val url = "https://sponsor.ajay.app/api/skipSegments".toHttpUrl().newBuilder()
                .addQueryParameter("videoID", videoId)
                .addQueryParameter(
                    "categories",
                    "[\"sponsor\",\"intro\",\"outro\",\"selfpromo\",\"music_offtopic\",\"preview\"]",
                )
                .build()
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.code == 404) return@use emptyList()
                if (!response.isSuccessful) return@use emptyList()
                val root = JSONArray(response.body.string())
                buildList {
                    for (index in 0 until root.length()) {
                        val item = root.optJSONObject(index) ?: continue
                        val segment = item.optJSONArray("segment") ?: continue
                        val start = (segment.optDouble(0, -1.0) * 1_000).toLong()
                        val end = (segment.optDouble(1, -1.0) * 1_000).toLong()
                        if (start >= 0 && end > start) {
                            add(SponsorSegment(start, end, item.optString("category")))
                        }
                    }
                }.sortedBy(SponsorSegment::startMs)
            }
        }.getOrDefault(emptyList())
    }
}
