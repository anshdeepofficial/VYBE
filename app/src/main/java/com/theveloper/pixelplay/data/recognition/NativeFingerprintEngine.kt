package com.theveloper.pixelplay.data.recognition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NativeFingerprintEngine {
    private val loadFailure: Throwable? = runCatching { System.loadLibrary("vybe_fingerprint") }.exceptionOrNull()

    private external fun encode(samples: ShortArray, sampleRate: Int): String?

    suspend fun fingerprint(samples: ShortArray): String = withContext(Dispatchers.Default) {
        loadFailure?.let { throw IllegalStateException("Fingerprint engine is unavailable", it) }
        require(samples.size in (16_000 * 5)..(16_000 * 15)) { "Invalid PCM duration" }
        require(samples.any { it.toInt() != 0 }) { "Empty PCM audio" }
        runCatching { encode(samples, 16_000) }
            .getOrElse { throw IllegalStateException("Fingerprint generation failed", it) }
            ?.takeIf(String::isNotBlank)
            ?: error("Fingerprint generation failed")
    }
}

