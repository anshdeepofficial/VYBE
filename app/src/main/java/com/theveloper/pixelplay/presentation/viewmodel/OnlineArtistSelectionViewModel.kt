package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeArtist
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ArtistSelectionType {
    PREFERRED,
    BLOCKED
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class OnlineArtistSelectionViewModel @Inject constructor(
    private val onlineMusicRepository: OnlineMusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<YouTubeArtist>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _selectionType = MutableStateFlow(ArtistSelectionType.PREFERRED)

    val selectedArtists: StateFlow<Set<String>> = _selectionType.flatMapLatest { type ->
        if (type == ArtistSelectionType.PREFERRED) {
            userPreferencesRepository.preferredArtists
        } else {
            userPreferencesRepository.blockedArtists
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    init {
        viewModelScope.launch {
            _query.debounce(500).distinctUntilChanged().collect { q ->
                if (q.isNotBlank()) {
                    _isLoading.value = true
                    val result = onlineMusicRepository.searchMusicStructured(q)
                    _searchResults.value = result.artists
                    _isLoading.value = false
                } else {
                    _searchResults.value = emptyList()
                    _isLoading.value = false
                }
            }
        }
    }

    fun setSelectionType(type: ArtistSelectionType) {
        _selectionType.value = type
        _query.value = ""
    }
    
    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun toggleArtist(artistName: String) {
        viewModelScope.launch {
            val type = _selectionType.value
            val current = if (type == ArtistSelectionType.PREFERRED) {
                userPreferencesRepository.preferredArtists.first()
            } else {
                userPreferencesRepository.blockedArtists.first()
            }
            
            val updated = if (current.contains(artistName)) {
                current - artistName
            } else {
                current + artistName
            }
            
            if (type == ArtistSelectionType.PREFERRED) {
                userPreferencesRepository.updatePreferredArtists(updated)
            } else {
                userPreferencesRepository.updateBlockedArtists(updated)
            }
        }
    }
}
