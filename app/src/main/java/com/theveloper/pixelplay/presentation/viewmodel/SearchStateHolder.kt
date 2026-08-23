package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.FlowPreview

/**
 * Manages search state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Search query execution
 * - Search filter management
 * - Search history CRUD operations
 */
@Singleton
class SearchStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val onlineMusicRepository: OnlineMusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private data class SearchRequest(
        val query: String,
        val requestId: Long,
    )

    // Search State
    private val _searchResults = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    val searchResults = _searchResults.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter = _selectedSearchFilter.asStateFlow()

    private val _searchHistory = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    val searchHistory = _searchHistory.asStateFlow()

    private val searchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val latestSearchRequestId = AtomicLong(0L)

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    /**
     * Initialize with ViewModel scope.
     */
    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        observeSearchRequests()
        scope.launch {
            userPreferencesRepository.searchHistoryEnabledFlow.collectLatest { enabled ->
                if (!enabled) _searchHistory.value = persistentListOf()
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchRequests() {
        searchJob?.cancel()
        searchJob = scope?.launch {
            searchRequests
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { request ->
                    val normalizedQuery = request.query

                    if (normalizedQuery.isBlank()) {
                        if (_searchResults.value.isNotEmpty()) {
                            _searchResults.value = persistentListOf()
                        }
                        return@collectLatest
                    }

                    try {
                        val currentFilter = _selectedSearchFilter.value
                        val resultsList = coroutineScope {
                            val localResults = async {
                                runCatching {
                                    musicRepository.searchAll(normalizedQuery, currentFilter).first()
                                }.getOrElse { error ->
                                    Timber.w(error, "Local search failed for query: %s", normalizedQuery)
                                    emptyList()
                                }
                            }
                            val onlineResults = async {
                                if (currentFilter == SearchFilterType.PLAYLISTS) {
                                    emptyList()
                                } else {
                                    runCatching {
                                        val region = userPreferencesRepository.userRegionFlow.first()
                                            .ifBlank { "IN" }
                                        val result = onlineMusicRepository.searchMusicStructured(
                                            normalizedQuery,
                                            region,
                                        )
                                        buildList {
                                            if (
                                                currentFilter == SearchFilterType.ALL ||
                                                currentFilter == SearchFilterType.SONGS
                                            ) {
                                                addAll(result.songs.map(SearchResultItem::SongItem))
                                            }
                                            if (
                                                currentFilter == SearchFilterType.ALL ||
                                                currentFilter == SearchFilterType.ALBUMS
                                            ) {
                                                addAll(result.albums.map { album ->
                                                    SearchResultItem.AlbumItem(album.toAlbum())
                                                })
                                            }
                                            if (
                                                currentFilter == SearchFilterType.ALL ||
                                                currentFilter == SearchFilterType.ARTISTS
                                            ) {
                                                addAll(result.artists.map { artist ->
                                                    SearchResultItem.ArtistItem(artist.toArtist())
                                                })
                                            }
                                            if (
                                                currentFilter == SearchFilterType.ALL ||
                                                currentFilter == SearchFilterType.VIDEOS
                                            ) {
                                                addAll(result.videos.map(SearchResultItem::VideoItem))
                                            }
                                        }
                                    }.getOrElse { error ->
                                        Timber.w(error, "Online search failed for query: %s", normalizedQuery)
                                        emptyList()
                                    }
                                }
                            }
                            (localResults.await() + onlineResults.await())
                                .distinctBy(::searchResultKey)
                        }

                        // Sort: prioritize Song/Album matches over Artist/Playlist matches.
                        val sortedResults = resultsList.sortedWith(
                            compareBy { result ->
                                when (result) {
                                    is SearchResultItem.SongItem -> 0
                                    is SearchResultItem.VideoItem -> 1
                                    is SearchResultItem.AlbumItem -> 2
                                    is SearchResultItem.ArtistItem -> 3
                                    is SearchResultItem.PlaylistItem -> 4
                                }
                            }
                        )

                        if (request.requestId != latestSearchRequestId.get()) {
                            return@collectLatest
                        }

                        val immutableResults = sortedResults.toImmutableList()
                        if (_searchResults.value != immutableResults) {
                            _searchResults.value = immutableResults
                        }
                    } catch (_: CancellationException) {
                        // Superseded by a newer query; ignore.
                    } catch (e: Exception) {
                        if (request.requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "Error performing search for query: $normalizedQuery")
                            _searchResults.value = persistentListOf()
                        }
                    }
                }
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        _selectedSearchFilter.value = filterType
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            try {
                if (!userPreferencesRepository.searchHistoryEnabledFlow.first()) {
                    _searchHistory.value = persistentListOf()
                    return@launch
                }
                val history = withContext(Dispatchers.IO) {
                    musicRepository.getRecentSearchHistory(limit)
                }
                _searchHistory.value = history.toImmutableList()
            } catch (e: Exception) {
                Timber.e(e, "Error loading search history")
            }
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank() && userPreferencesRepository.searchHistoryEnabledFlow.first()) {
                try {
                    withContext(Dispatchers.IO) {
                        musicRepository.addSearchHistoryItem(query)
                    }
                    loadSearchHistory()
                } catch (e: Exception) {
                    Timber.e(e, "Error adding search history item")
                }
            }
        }
    }

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()

        val requestId = latestSearchRequestId.incrementAndGet()

        if (normalizedQuery.isBlank()) {
            if (_searchResults.value.isNotEmpty()) {
                _searchResults.value = persistentListOf()
            }
        }

        searchRequests.tryEmit(SearchRequest(normalizedQuery, requestId))
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.deleteSearchHistoryItemByQuery(query)
                }
                loadSearchHistory()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting search history item")
            }
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.clearSearchHistory()
                }
                _searchHistory.value = persistentListOf()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing search history")
            }
        }
    }

    fun onCleared() {
        searchJob?.cancel()
        scope = null
    }

    private fun searchResultKey(result: SearchResultItem): String = when (result) {
        is SearchResultItem.SongItem -> "song:${result.song.id}"
        is SearchResultItem.VideoItem -> "video:${result.song.id}"
        is SearchResultItem.AlbumItem -> "album:${result.album.id}"
        is SearchResultItem.ArtistItem -> "artist:${result.artist.remoteBrowseId ?: result.artist.id}"
        is SearchResultItem.PlaylistItem -> "playlist:${result.playlist.id}"
    }
}
