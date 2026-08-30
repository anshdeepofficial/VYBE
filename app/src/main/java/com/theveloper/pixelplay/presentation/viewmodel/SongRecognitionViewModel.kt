package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import com.theveloper.pixelplay.data.recognition.AcoustIdRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SongRecognitionUiState(
    val isSearching: Boolean = false,
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

    fun resolve(candidates: List<String>) {
        val query = candidates.firstOrNull().orEmpty()
        if (query.isBlank()) {
            _state.value = SongRecognitionUiState(message = "No song name was recognized. Try again.")
            return
        }
        viewModelScope.launch {
            _state.value = SongRecognitionUiState(isSearching = true, query = query)
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
        viewModelScope.launch {
            _state.value = SongRecognitionUiState(isSearching = true, message = "Listening for 12 seconds…")
            runCatching { AcoustIdRecognizer.listenAndIdentify() }
                .onSuccess { resolve(listOf(it)) }
                .onFailure { error ->
                    _state.value = SongRecognitionUiState(message = error.message ?: "Recognition failed")
                }
        }
    }
}
