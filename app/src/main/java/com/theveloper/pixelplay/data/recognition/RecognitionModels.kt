package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.data.model.Song

data class RecognitionMetadata(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val isrc: String? = null,
)

sealed interface RecognitionResult {
    data class Match(val metadata: RecognitionMetadata, val song: Song?) : RecognitionResult
    data object NoMatch : RecognitionResult
    data class Failure(val reason: RecognitionFailure, val detail: String? = null) : RecognitionResult
}

enum class RecognitionFailure {
    PERMISSION_DENIED, MICROPHONE_UNAVAILABLE, INVALID_AUDIO, FINGERPRINT_FAILED,
    OFFLINE, TIMEOUT, RATE_LIMITED, HTTP_ERROR, MALFORMED_RESPONSE, CANCELLED,
}

interface RecognitionProvider {
    suspend fun recognize(fingerprint: String, durationSeconds: Int): RecognitionMetadata?
}

