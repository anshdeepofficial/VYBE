package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.recommendation.RecommendationProfile
import com.theveloper.pixelplay.data.recommendation.RecommendationSurface
import com.theveloper.pixelplay.data.recommendation.UnifiedRecommendationEngine

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryStateHolder: LibraryStateHolder,
    private val recommendationEngine: UnifiedRecommendationEngine,
) : ViewModel() {

    fun rankRecommendations(songs: List<Song>): List<Song> = recommendationEngine.rank(
        candidates = songs,
        profile = RecommendationProfile.fromTaste(songs),
        surface = RecommendationSurface.LIBRARY,
        limit = 100,
    )

    val songsPagingFlow = libraryStateHolder.songsPagingFlow.cachedIn(viewModelScope)

    val albumsPagingFlow = libraryStateHolder.albumsPagingFlow.cachedIn(viewModelScope)

    val artistsPagingFlow = libraryStateHolder.artistsPagingFlow.cachedIn(viewModelScope)

    val favoritesPagingFlow = libraryStateHolder.favoritesPagingFlow.cachedIn(viewModelScope)

    val favoriteSongCountFlow = libraryStateHolder.favoriteSongCountFlow

    val isLoadingLibrary = libraryStateHolder.isLoadingLibrary
}
