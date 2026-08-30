package com.theveloper.pixelplay.data.recognition

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.theveloper.pixelplay.BuildConfig
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object AcoustIdRecognizer {
    private const val RATE = 44100
    private const val SECONDS = 12
    private val client = OkHttpClient()

    @SuppressLint("MissingPermission")
    suspend fun listenAndIdentify(): String = withContext(Dispatchers.IO) {
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(minimum > 0) { "Microphone is unavailable" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minimum * 2)
        val pcm = ShortArray(RATE * SECONDS)
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not start" }
            recorder.startRecording()
            var offset = 0
            while (offset < pcm.size) {
                val count = recorder.read(pcm, offset, minOf(minimum, pcm.size - offset), AudioRecord.READ_BLOCKING)
                if (count < 0) error("Audio capture failed ($count)")
                offset += count
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        val fingerprint = ChromaprintBridge.fingerprint(pcm, RATE) ?: error("Could not fingerprint this audio")
        val encoded = URLEncoder.encode(fingerprint, "UTF-8")
        val url = "https://api.acoustid.org/v2/lookup?client=${BuildConfig.ACOUSTID_CLIENT_KEY}" +
            "&meta=recordings+releasegroups&duration=$SECONDS&fingerprint=$encoded"
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("AcoustID request failed (${response.code})")
            response.body?.string() ?: error("Empty AcoustID response")
        }
        val root = JSONObject(body)
        val results = root.optJSONArray("results") ?: error("No match found. Play a clear part of the song and retry.")
        if (results.length() == 0) error("No match found. Play a clear part of the song and retry.")
        val recordings = results.getJSONObject(0).optJSONArray("recordings") ?: error("No recording metadata found")
        val recording = recordings.getJSONObject(0)
        val artists = recording.optJSONArray("artists")
        val artist = if (artists != null && artists.length() > 0) artists.getJSONObject(0).optString("name") else ""
        listOf(recording.optString("title"), artist).filter { it.isNotBlank() }.joinToString(" ")
    }
}
