package com.theveloper.pixelplay.data.network.ytmusic

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

class NewPipeStreamResolverIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "PIXELPLAY_RUN_NETWORK_TESTS", matches = "true")
    fun resolvesAndServesAYouTubeAudioStream() {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val streamUrl = NewPipeStreamResolver(client).resolve("dQw4w9WgXcQ")

        val debugDetails = if (streamUrl == null) {
            // Re-run without the production catch so the extractor's exact failure is visible.
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            "audio=${info.audioStreams.map { "${it.deliveryMethod}/${it.isUrl}/${it.content.take(30)}" }} " +
                "video=${info.videoStreams.size} videoOnly=${info.videoOnlyStreams.size}"
        } else ""
        assertTrue(
            streamUrl?.startsWith("http") == true,
            "Expected a signed HTTP stream URL, got: $streamUrl; $debugDetails"
        )

        val request = Request.Builder()
            .url(requireNotNull(streamUrl))
            .header("Range", "bytes=0-1023")
            .build()
        client.newCall(request).execute().use { response ->
            assertTrue(response.isSuccessful, "Stream returned HTTP ${response.code}")
            assertTrue(response.body.contentLength() != 0L, "Stream response was empty")
        }
    }
}
