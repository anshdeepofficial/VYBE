package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.recognition.AcoustIdRecognizer
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SongRecognitionUiState(
    val isSearching: Boolean = false,
    val isListening: Boolean = false,
    val query: String = "",
    val matches: List<Song> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class SongRecognitionViewModel @Inject constructor(
    private val onlineMusicRepository: OnlineMusicRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SongRecognitionUiState())
    val state: StateFlow<SongRecognitionUiState> = _state.asStateFlow()
    private var recognitionJob: Job? = null

    fun resolve(candidates: List<String>) {
        val query = candidates.firstOrNull().orEmpty()
        if (query.isBlank()) {
            _state.value = SongRecognitionUiState(message = "No song was recognized. Try again.")
            return
        }
        viewModelScope.launch {
            _state.value = SongRecognitionUiState(isSearching = true, query = query, message = "Finding it in VYBE…")
            runCatching { onlineMusicRepository.searchSongs(query).take(10) }
                .onSuccess { songs ->
                    _state.value = SongRecognitionUiState(
                        query = query,
                        matches = songs,
                        message = if (songs.isEmpty()) "No playable VYBE result found for “$query”." else null,
                    )
                }
                .onFailure { error ->
                    _state.value = SongRecognitionUiState(query = query, message = error.message ?: "Recognition search failed")
                }
        }
    }

    fun listen() {
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            _state.value = SongRecognitionUiState(
                isSearching = true,
                isListening = true,
                message = "Listening for 12 seconds…",
            )
            try {
                val identified = AcoustIdRecognizer.listenAndIdentify()
                _state.value = _state.value.copy(isListening = false, message = "Song identified. Finding it in VYBE…")
                resolve(listOf(identified))
            } catch (_: CancellationException) {
                // stopListening() sets the final state.
            } catch (error: Throwable) {
                _state.value = SongRecognitionUiState(message = error.message ?: "Recognition failed")
            } finally {
                recognitionJob = null
            }
        }
    }

    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        _state.value = SongRecognitionUiState(message = "Listening stopped.")
    }
}
