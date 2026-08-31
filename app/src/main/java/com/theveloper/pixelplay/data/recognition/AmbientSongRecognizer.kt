package com.theveloper.pixelplay.data.recognition

import android.content.Context
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class AmbientSongRecognizer(
    context: Context,
    private val provider: RecognitionProvider,
    private val resolver: RecognitionTrackResolver,
) {
    private val capture = MicrophoneAudioCapture(context.applicationContext)
    private val sessionMutex = Mutex()

    suspend fun recognizeSong(): RecognitionResult = sessionMutex.withLock {
        try {
            withTimeout(32_000L) {
                val captured = capture.capture(10)
                val pcm16Khz = Pcm16Resampler.to16KhzMono(captured)
                val fingerprint = NativeFingerprintEngine.fingerprint(pcm16Khz)
                val metadata = provider.recognize(fingerprint, pcm16Khz.size / 16_000)
                    ?: return@withTimeout RecognitionResult.NoMatch
                RecognitionResult.Match(metadata, resolver.resolve(metadata))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            RecognitionResult.Failure(
                reason = when (error) {
                    is SecurityException -> RecognitionFailure.PERMISSION_DENIED
                    is SocketTimeoutException -> RecognitionFailure.TIMEOUT
                    is UnknownHostException -> RecognitionFailure.OFFLINE
                    is RateLimitedException -> RecognitionFailure.RATE_LIMITED
                    is RecognitionHttpException -> RecognitionFailure.HTTP_ERROR
                    is MalformedRecognitionException -> RecognitionFailure.MALFORMED_RESPONSE
                    is IllegalArgumentException -> RecognitionFailure.INVALID_AUDIO
                    is IllegalStateException -> when {
                        error.message?.contains("permission", true) == true -> RecognitionFailure.PERMISSION_DENIED
                        error.message?.contains("Microphone", true) == true -> RecognitionFailure.MICROPHONE_UNAVAILABLE
                        error.message?.contains("Fingerprint", true) == true -> RecognitionFailure.FINGERPRINT_FAILED
                        else -> RecognitionFailure.INVALID_AUDIO
                    }
                    else -> RecognitionFailure.HTTP_ERROR
                },
                detail = error.message,
            )
        }
    }
}
