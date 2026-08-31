package com.theveloper.pixelplay.data.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class CapturedPcm(val samples: ShortArray, val sampleRate: Int)

class MicrophoneAudioCapture(private val context: Context) {
    @SuppressLint("MissingPermission")
    suspend fun capture(seconds: Int = 10): CapturedPcm = withContext(Dispatchers.IO) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission denied"
        }
        val sampleRate = listOf(16_000, 44_100, 48_000).firstOrNull { rate ->
            AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) > 0
        } ?: error("Microphone is unavailable")
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        var recorder: AudioRecord? = null
        try {
            for (source in listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)) {
                val candidate = runCatching {
                    AudioRecord(
                        source,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minimum * 2, sampleRate),
                    )
                }.getOrNull() ?: continue
                val started = candidate.state == AudioRecord.STATE_INITIALIZED && runCatching {
                    candidate.startRecording()
                    candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING
                }.getOrDefault(false)
                if (started) {
                    recorder = candidate
                    break
                } else {
                    runCatching { candidate.release() }
                }
            }
            val activeRecorder = checkNotNull(recorder) { "Microphone could not initialize" }
            val output = ShortArray(sampleRate * seconds)
            var offset = 0
            var emptyReads = 0
            while (offset < output.size) {
                coroutineContext.ensureActive()
                val read = activeRecorder.read(output, offset, minOf(minimum, output.size - offset), AudioRecord.READ_BLOCKING)
                when {
                    read > 0 -> { offset += read; emptyReads = 0 }
                    read == 0 && ++emptyReads < 20 -> continue
                    read == 0 -> error("Microphone returned no audio")
                    else -> error("Audio capture failed ($read)")
                }
            }
            check(output.any { it.toInt() != 0 }) { "No audible audio was captured" }
            CapturedPcm(output, sampleRate)
        } finally {
            recorder?.let { active ->
                runCatching { if (active.recordingState == AudioRecord.RECORDSTATE_RECORDING) active.stop() }
                runCatching { active.release() }
            }
        }
    }
}
