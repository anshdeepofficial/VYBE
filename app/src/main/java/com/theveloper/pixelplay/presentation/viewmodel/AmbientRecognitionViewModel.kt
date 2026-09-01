package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.recognition.AmbientSongRecognizer
import com.theveloper.pixelplay.data.recognition.RecognitionResult
import com.theveloper.pixelplay.data.recognition.RecognitionTrackResolver
import com.theveloper.pixelplay.data.recognition.ShazamRecognitionProvider
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AmbientRecognitionPhase { READY, LISTENING, PROCESSING, SUCCESS, NO_MATCH, ERROR }

data class AmbientRecognitionUiState(
    val phase: AmbientRecognitionPhase = AmbientRecognitionPhase.READY,
    val elapsedSeconds: Int = 0,
    val result: RecognitionResult.Match? = null,
    val message: String? = null,
)

@HiltViewModel
class AmbientRecognitionViewModel @Inject constructor(
    @ApplicationContext context: Context,
    repository: OnlineMusicRepository,
    recognitionProvider: ShazamRecognitionProvider,
) : ViewModel() {
    private val recognizer = AmbientSongRecognizer(
        context,
        recognitionProvider,
        RecognitionTrackResolver(repository),
    )
    private val _state = MutableStateFlow(AmbientRecognitionUiState())
    val state: StateFlow<AmbientRecognitionUiState> = _state.asStateFlow()
    private var recognitionJob: Job? = null

    fun start() {
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            try {
                _state.value = AmbientRecognitionUiState(AmbientRecognitionPhase.LISTENING)
                coroutineScope {
                    val timer = launch {
                        repeat(14) { second ->
                            _state.value = _state.value.copy(elapsedSeconds = second + 1)
                            delay(1_000)
                        }
                        _state.value = _state.value.copy(phase = AmbientRecognitionPhase.PROCESSING)
                    }
                    val result = recognizer.recognizeSong()
                    timer.cancel()
                    _state.value = when (result) {
                        is RecognitionResult.Match -> AmbientRecognitionUiState(AmbientRecognitionPhase.SUCCESS, result = result)
                        RecognitionResult.NoMatch -> AmbientRecognitionUiState(AmbientRecognitionPhase.NO_MATCH, message = "No match found. Try a clear, louder part of the original recording.")
                        is RecognitionResult.Failure -> AmbientRecognitionUiState(AmbientRecognitionPhase.ERROR, message = friendlyMessage(result))
                    }
                }
            } catch (_: CancellationException) {
                _state.value = AmbientRecognitionUiState()
            }
        }
    }

    fun cancel() {
        recognitionJob?.cancel()
        recognitionJob = null
        _state.value = AmbientRecognitionUiState()
    }

    fun permissionDenied() {
        _state.value = AmbientRecognitionUiState(
            phase = AmbientRecognitionPhase.ERROR,
            message = "Microphone permission is required for ambient song recognition.",
        )
    }

    private fun friendlyMessage(failure: RecognitionResult.Failure): String = when (failure.reason) {
        com.theveloper.pixelplay.data.recognition.RecognitionFailure.PERMISSION_DENIED -> "Microphone permission is required."
        com.theveloper.pixelplay.data.recognition.RecognitionFailure.MICROPHONE_UNAVAILABLE -> "The microphone is unavailable. Close other recording apps and retry."
        com.theveloper.pixelplay.data.recognition.RecognitionFailure.OFFLINE -> "You are offline. Connect to the internet and retry."
        com.theveloper.pixelplay.data.recognition.RecognitionFailure.TIMEOUT -> "Recognition timed out. Please retry."
        com.theveloper.pixelplay.data.recognition.RecognitionFailure.RATE_LIMITED -> "Recognition is busy. Wait a moment and retry."
        else -> failure.detail ?: "Recognition could not complete. Please retry."
    }

    override fun onCleared() {
        recognitionJob?.cancel()
        super.onCleared()
    }
}
