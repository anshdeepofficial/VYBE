package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val preferredGenres: Flow<Set<String>> = userPreferencesRepository.preferredGenres
    val preferredArtists: Flow<Set<String>> = userPreferencesRepository.preferredArtists
    val userRegion: Flow<String> = userPreferencesRepository.userRegionFlow

    fun updateUserRegion(region: String) {
        viewModelScope.launch {
            userPreferencesRepository.setUserRegion(region)
        }
    }

    fun updatePreferredGenres(genres: Set<String>) {
        viewModelScope.launch {
            userPreferencesRepository.updatePreferredGenres(genres)
        }
    }

    fun updatePreferredArtists(artists: Set<String>) {
        viewModelScope.launch {
            userPreferencesRepository.updatePreferredArtists(artists)
        }
    }
}
