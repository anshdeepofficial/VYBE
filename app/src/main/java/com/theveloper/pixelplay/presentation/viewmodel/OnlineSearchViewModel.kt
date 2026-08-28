package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.ai.AiPlaylistGenerator
import com.theveloper.pixelplay.data.database.AudiusFavoriteDao
import com.theveloper.pixelplay.data.database.toAudiusFavoriteEntity
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeAlbum
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeArtist
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeMusicEngine
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeSearchResult
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeAccountManager
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class OnlineSearchFilter {
    ALL,
    SONGS,
    ALBUMS,
    ARTISTS
}

@HiltViewModel
class OnlineSearchViewModel @Inject constructor(
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val userPrefs: UserPreferencesRepository,
    private val favoriteDao: AudiusFavoriteDao,
    private val repository: OnlineMusicRepository,
    private val engine: YouTubeMusicEngine,
    private val musicRepository: MusicRepository,
    private val youTubeAccountManager: YouTubeAccountManager,
) : ViewModel() {

    private val _searchFilter = MutableStateFlow(OnlineSearchFilter.ALL)
    val searchFilter: StateFlow<OnlineSearchFilter> = _searchFilter.asStateFlow()

    private val _searchResultsSongs = MutableStateFlow<List<Song>>(emptyList())
    val searchResultsSongs: StateFlow<List<Song>> = _searchResultsSongs.asStateFlow()

    private val _searchResultsAlbums = MutableStateFlow<List<YouTubeAlbum>>(emptyList())
    val searchResultsAlbums: StateFlow<List<YouTubeAlbum>> = _searchResultsAlbums.asStateFlow()

    private val _searchResultsArtists = MutableStateFlow<List<YouTubeArtist>>(emptyList())
    val searchResultsArtists: StateFlow<List<YouTubeArtist>> = _searchResultsArtists.asStateFlow()

    // Backward compatibility for legacy observers
    val searchResults: StateFlow<List<Song>> = _searchResultsSongs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _trendingTracks = MutableStateFlow<List<Song>>(emptyList())
    val trendingTracks: StateFlow<List<Song>> = _trendingTracks.asStateFlow()
    private var baseTrendingTracks: List<Song> = emptyList()

    private val _latestReleaseTracks = MutableStateFlow<List<Song>>(emptyList())
    val latestReleaseTracks: StateFlow<List<Song>> = _latestReleaseTracks.asStateFlow()

    private val _discoveryArtists = MutableStateFlow<List<YouTubeArtist>>(emptyList())
    val discoveryArtists: StateFlow<List<YouTubeArtist>> = _discoveryArtists.asStateFlow()

    private val _discoveryTitle = MutableStateFlow("Trending Now")
    val discoveryTitle: StateFlow<String> = _discoveryTitle.asStateFlow()

    private val _aiRecommendations = MutableStateFlow<List<Song>>(emptyList())
    val aiRecommendations: StateFlow<List<Song>> = _aiRecommendations.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<com.theveloper.pixelplay.data.model.SearchHistoryItem>>(emptyList())
    val recentSearches: StateFlow<List<com.theveloper.pixelplay.data.model.SearchHistoryItem>> = _recentSearches.asStateFlow()

    val searchHistoryEnabled: StateFlow<Boolean> = userPrefs.searchHistoryEnabledFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        true,
    )

    val isYouTubeMusicConnected: StateFlow<Boolean> = youTubeAccountManager.isLoggedInFlow
    val accountInterestLabels: StateFlow<List<String>> = youTubeAccountManager.interestLabelsFlow

    private val _querySuggestions = MutableStateFlow<List<String>>(emptyList())
    val querySuggestions: StateFlow<List<String>> = _querySuggestions.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    val favoriteIds = favoriteDao.getAllFavorites()

    private var currentQuery: String = ""
    private var searchJob: Job? = null
    private var discoveryJob: Job? = null
    private var suggestionJob: Job? = null
    private val structuredSearchCache = ConcurrentHashMap<String, YouTubeSearchResult>()

    fun setFilter(filter: OnlineSearchFilter) {
        _searchFilter.value = filter
    }

    fun toggleFavorite(song: Song, isFav: Boolean) {
        viewModelScope.launch {
            if (isFav) favoriteDao.delete(song.id) else favoriteDao.insert(song.toAudiusFavoriteEntity())
        }
    }

    init {
        loadTrendingAndRecommendations()
        viewModelScope.launch {
            searchHistoryEnabled.collectLatest { enabled ->
                if (enabled) loadRecentSearches() else _recentSearches.value = emptyList()
            }
        }
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            _recentSearches.value = if (searchHistoryEnabled.value) {
                musicRepository.getRecentSearchHistory(12)
            } else {
                emptyList()
            }
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            musicRepository.deleteSearchHistoryItemByQuery(query)
            loadRecentSearches()
        }
    }

    fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        if (searchHistoryEnabled.value) {
            viewModelScope.launch {
                musicRepository.addSearchHistoryItem(trimmed)
                loadRecentSearches()
            }
        }
        search(trimmed, debounce = false)
    }

    fun rememberSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        if (searchHistoryEnabled.value) {
            viewModelScope.launch {
                musicRepository.addSearchHistoryItem(trimmed)
                loadRecentSearches()
            }
        }
    }

    fun fetchTrending() {
        loadTrendingAndRecommendations()
    }

    private fun loadTrendingAndRecommendations() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val region = normalizedRegion(userPrefs.userRegionFlow.first())
                val preferredArtists = userPrefs.preferredArtists.first().toList()
                val interests = (youTubeAccountManager.interestLabelsFlow.value + preferredArtists)
                    .filter(String::isNotBlank)
                    .distinctBy(String::lowercase)
                val fastRequest = async { repository.getFastPersonalizedDiscovery(interests) }
                val trendingRequest = async { runCatching { repository.getTrendingTracks(region) } }
                val releasesRequest = async { runCatching { repository.getLatestReleases(region) } }

                val fastTracks = withTimeoutOrNull(3_500L) { fastRequest.await() }.orEmpty()
                if (fastTracks.isNotEmpty()) {
                    baseTrendingTracks = fastTracks
                    _trendingTracks.value = fastTracks
                    _aiRecommendations.value = rankForInterests(fastTracks, interests).take(12)
                    _searchError.value = null
                    _isLoading.value = false
                }

                val trendingResult = withTimeoutOrNull(10_000L) { trendingRequest.await() }
                    ?: Result.success(emptyList())
                val releasesResult = withTimeoutOrNull(10_000L) { releasesRequest.await() }
                    ?: Result.success(emptyList())
                val tracks = (rankForInterests(trendingResult.getOrDefault(emptyList()), interests) + fastTracks)
                    .distinctBy { it.id }
                val releases = releasesResult.getOrDefault(emptyList())
                if (tracks.isEmpty() && releases.isEmpty()) {
                    throw trendingResult.exceptionOrNull()
                        ?: releasesResult.exceptionOrNull()
                        ?: IllegalStateException("YouTube Music discovery returned no tracks")
                }
                baseTrendingTracks = tracks
                _trendingTracks.value = tracks.ifEmpty { releases }
                _latestReleaseTracks.value = releases
                _discoveryTitle.value = "Trending Now"
                if (tracks.isNotEmpty()) {
                    _aiRecommendations.value = rankForInterests(tracks, interests).take(12)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _trendingTracks.value = emptyList()
                _searchError.value = error.message ?: "Could not load YouTube Music discovery"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun generateAiRecommendations(candidates: List<Song>) {
        viewModelScope.launch {
            val genres: Set<String> = userPrefs.preferredGenres.first()
            val artists: Set<String> = userPrefs.preferredArtists.first()
            if (genres.isEmpty() && artists.isEmpty()) {
                _aiRecommendations.value = candidates.take(12)
                return@launch
            }

            val genreStr = genres.joinToString(", ")
            val artistStr = artists.joinToString(", ")
            val prompt = "User prefers genres: $genreStr. Favorite artists: $artistStr. " +
                "Filter and rank the best matching songs for them from the candidate list."

            val result = aiPlaylistGenerator.generate(
                userPrompt = prompt,
                allSongs = emptyList(),
                minLength = 5,
                maxLength = 20,
                candidateSongs = candidates,
                type = com.theveloper.pixelplay.data.ai.AiSystemPromptType.PLAYLIST
            )

            result.onSuccess { recommended ->
                _aiRecommendations.value = recommended
            }.onFailure {
                _aiRecommendations.value = candidates.take(12)
            }
        }
    }

    /** Search YouTube Music for a genre chip or keyword. */
    fun searchByGenre(genre: String) {
        search(genre, debounce = false)
    }

    /** Loads an idle-screen discovery feed without turning the chip into a search query. */
    fun selectDiscovery(label: String) {
        searchJob?.cancel()
        when (label) {
            "Trending" -> {
                _discoveryArtists.value = emptyList()
                _trendingTracks.value = baseTrendingTracks
                _discoveryTitle.value = "Trending Now"
                if (baseTrendingTracks.isEmpty()) loadTrendingAndRecommendations()
            }
            "Latest Releases" -> {
                _discoveryArtists.value = emptyList()
                _discoveryTitle.value = "Latest Releases"
                if (_latestReleaseTracks.value.isNotEmpty()) {
                    _trendingTracks.value = _latestReleaseTracks.value
                } else {
                    searchJob = viewModelScope.launch {
                        _isLoading.value = true
                        try {
                            val region = userPrefs.userRegionFlow.first().ifBlank { "IN" }
                            val releases = repository.getLatestReleases(region)
                            _latestReleaseTracks.value = releases
                            _trendingTracks.value = releases
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            _trendingTracks.value = emptyList()
                            _searchError.value = error.message ?: "Could not load latest releases"
                        } finally {
                            _isLoading.value = false
                        }
                    }
                }
            }
            else -> {
                searchJob = viewModelScope.launch {
                    _isLoading.value = true
                    _searchError.value = null
                    try {
                        val region = userPrefs.userRegionFlow.first().ifBlank { "IN" }
                        val result = repository.searchMusicStructured(label, region)
                        _trendingTracks.value = result.songs
                        _discoveryArtists.value = result.artists
                        _discoveryTitle.value = "Because you listen to $label"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        _trendingTracks.value = emptyList()
                        _searchError.value = error.message ?: "Could not load personalized discovery"
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }
    }

    /** Clear search results and return to trending/recommendation view. */
    fun clearSearch() {
        searchJob?.cancel()
        suggestionJob?.cancel()
        currentQuery = ""
        _searchResultsSongs.value = emptyList()
        _searchResultsAlbums.value = emptyList()
        _searchResultsArtists.value = emptyList()
        _discoveryArtists.value = emptyList()
        _querySuggestions.value = emptyList()
        _searchError.value = null
        _isLoading.value = false
        if (_trendingTracks.value.isEmpty()) loadTrendingAndRecommendations()
    }

    fun search(query: String, debounce: Boolean = true) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            clearSearch()
            return
        }

        // Search has priority over the heavier discovery feed. Running both at once was
        // exhausting the same HTTP pool and making interactive queries appear stuck/empty.
        discoveryJob?.cancel()
        currentQuery = trimmed
        _searchError.value = null
        _querySuggestions.value = emptyList()

        // Cache hit
        structuredSearchCache[trimmed.lowercase()]?.let { cached ->
            _searchResultsSongs.value = cached.songs
            _searchResultsAlbums.value = cached.albums
            _searchResultsArtists.value = cached.artists
            _isLoading.value = false
            loadSuggestions(trimmed)
            return
        }

        searchJob = viewModelScope.launch {
            if (debounce) {
                delay(250) // Fast 250ms debounce
            }
            _isLoading.value = true
            try {
                val region = normalizedRegion(userPrefs.userRegionFlow.first())
                val result = supervisorScope {
                    val youtubeRequest = async {
                        runCatching { repository.searchYouTubeMusicStructured(trimmed, region) }
                            .getOrDefault(YouTubeSearchResult())
                    }
                    val fallbackRequest = async {
                        runCatching { repository.searchFallbackSongs(trimmed) }.getOrDefault(emptyList())
                    }

                    // Await the primary YouTube search first. It is usually much faster than Saavn.
                    val youtubeResult = withTimeoutOrNull(10_000L) { youtubeRequest.await() }
                        ?: YouTubeSearchResult()

                    // If YouTube returned results, we can show them immediately.
                    if (youtubeResult.songs.isNotEmpty() || youtubeResult.albums.isNotEmpty() || youtubeResult.artists.isNotEmpty()) {
                        if (currentQuery == trimmed) {
                            _searchResultsSongs.value = rankSearchSongs(trimmed, youtubeResult.songs, retainRelated = true)
                            _searchResultsAlbums.value = youtubeResult.albums
                            _searchResultsArtists.value = youtubeResult.artists
                            _isLoading.value = false
                        }
                    }

                    // Now wait for the fallback results to augment the list, but don't block the initial render.
                    val fallbackSongs = withTimeoutOrNull(3_500L) { fallbackRequest.await() }
                        .orEmpty()
                        .let { rankSearchSongs(trimmed, it, retainRelated = false) }

                    youtubeResult.copy(
                        songs = rankSearchSongs(trimmed, youtubeResult.songs, retainRelated = true)
                            .plus(fallbackSongs)
                            .distinctBy { it.id }
                    )
                }
                if (currentQuery != trimmed) return@launch
                val rankedResult = result.copy(albums = rankSearchAlbums(trimmed, result.albums))
                if (rankedResult.songs.isNotEmpty() || rankedResult.albums.isNotEmpty() || rankedResult.artists.isNotEmpty()) {
                    structuredSearchCache[trimmed.lowercase()] = rankedResult
                }
                _searchResultsSongs.value = rankedResult.songs
                _searchResultsAlbums.value = rankedResult.albums
                _searchResultsArtists.value = rankedResult.artists
                loadSuggestions(trimmed)
            } catch (cancelled: CancellationException) {
                // A newer query superseded this request. Cancellation is expected and
                // must never be rendered as a search error.
                throw cancelled
            } catch (error: Exception) {
                if (currentQuery == trimmed) {
                    _searchResultsSongs.value = emptyList()
                    _searchResultsAlbums.value = emptyList()
                    _searchResultsArtists.value = emptyList()
                    _searchError.value = error.message ?: "Search could not reach YouTube Music"
                }
            } finally {
                if (currentQuery == trimmed) _isLoading.value = false
            }
        }
    }

    private fun loadSuggestions(query: String) {
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(120)
            val region = normalizedRegion(runCatching { userPrefs.userRegionFlow.first() }.getOrDefault("IN"))
            val suggestions = withTimeoutOrNull(3_000L) { engine.getSearchSuggestions(query, region) }.orEmpty()
            if (currentQuery == query) _querySuggestions.value = suggestions
        }
    }

    private fun normalizedRegion(region: String): String =
        region.takeUnless { it.isBlank() || it.equals("Global", ignoreCase = true) } ?: "IN"

    private fun rankSearchSongs(
        query: String,
        songs: List<Song>,
        retainRelated: Boolean,
    ): List<Song> {
        val normalizedQuery = query.trim().lowercase()
        val tokens = normalizedQuery.split(Regex("\\s+")).filter { it.length > 1 }
        return songs.filter(::isDisplayableMusicSong).distinctBy { it.id }
            .map { song ->
                val title = song.title.lowercase()
                val artist = song.artist.lowercase()
                val album = song.album.lowercase()
                val score = when {
                    title == normalizedQuery -> 10000
                    title.startsWith(normalizedQuery) -> 5000
                    title.contains(normalizedQuery) -> 2000
                    artist.contains(normalizedQuery) -> 1000
                    album.contains(normalizedQuery) -> 500
                    else -> tokens.sumOf { token ->
                        (if (title.contains(token)) 20 else 0) +
                            (if (artist.contains(token)) 12 else 0) +
                            (if (album.contains(token)) 8 else 0)
                    }
                }
                song to score
            }
            .filter { (_, score) -> retainRelated || score > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun rankSearchAlbums(query: String, albums: List<YouTubeAlbum>): List<YouTubeAlbum> {
        val normalizedQuery = query.trim().lowercase()
        val tokens = normalizedQuery.split(Regex("\\s+")).filter { it.length > 1 }
        return albums
            .filter { album ->
                album.browseId.isNotBlank() && album.title.isNotBlank() &&
                    album.type.lowercase() in setOf("album", "single", "ep", "soundtrack")
            }
            .distinctBy { it.browseId }
            .map { album ->
                val title = album.title.lowercase()
                val artist = album.artist.lowercase()
                val score = when {
                    title == normalizedQuery -> 10000
                    "$artist $title".contains(normalizedQuery) -> 8000
                    title.startsWith(normalizedQuery) -> 5000
                    title.contains(normalizedQuery) -> 2000
                    artist == normalizedQuery -> 1000
                    else -> tokens.sumOf { token ->
                        (if (title.contains(token)) 24 else 0) + (if (artist.contains(token)) 12 else 0)
                    }
                }
                album to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun isDisplayableMusicSong(song: Song): Boolean {
        val metadata = "${song.title} ${song.artist} ${song.album}".lowercase()
        val nonMusicTerms = listOf(
            "podcast", "episode", "episodes", "audiobook", "audio book",
            "interview", "talk show", "press conference", "keynote", "news"
        )
        return song.title.isNotBlank() && song.artist.isNotBlank() && nonMusicTerms.none(metadata::contains)
    }

    private fun rankForInterests(songs: List<Song>, interests: List<String>): List<Song> {
        val keys = interests.map { it.lowercase() }
        return songs.distinctBy { it.id }.sortedByDescending { song ->
            val metadata = "${song.title} ${song.artist} ${song.album} ${song.genre.orEmpty()}".lowercase()
            keys.count(metadata::contains)
        }
    }

    fun loadMore() {
        // High density results loaded in one shot
    }

    suspend fun resolveStreamUrl(song: Song): String? {
        return engine.resolveStreamUrl(song.id)
    }

    /** Resolve missing album navigation metadata for search/fallback results on demand. */
    suspend fun resolveAlbumBrowseId(song: Song): String? {
        song.remoteAlbumBrowseId?.takeIf(String::isNotBlank)?.let { return it }
        val wantedAlbum = song.album.trim()
        _searchResultsAlbums.value.firstOrNull {
            it.title.equals(wantedAlbum, ignoreCase = true)
        }?.let { return it.browseId }
        val region = normalizedRegion(runCatching { userPrefs.userRegionFlow.first() }.getOrDefault("IN"))
        val hasSpecificAlbum = wantedAlbum.isNotBlank() && !wantedAlbum.equals("YouTube Music", ignoreCase = true)
        val lookupQuery = if (hasSpecificAlbum) "$wantedAlbum ${song.artist}" else "${song.title} ${song.artist}"
        return withTimeoutOrNull(8_000L) {
            val matches = repository.searchYouTubeMusicStructured(lookupQuery, region).albums
            (if (hasSpecificAlbum) {
                matches.firstOrNull {
                    it.title.equals(wantedAlbum, ignoreCase = true) ||
                        it.title.contains(wantedAlbum, ignoreCase = true) ||
                        wantedAlbum.contains(it.title, ignoreCase = true)
                }
            } else matches.firstOrNull())?.browseId
        }
    }

    /** Resolve the primary artist page when a result did not include linked-artist metadata. */
    suspend fun resolveArtistBrowseId(song: Song): String? {
        song.artists.firstNotNullOfOrNull { it.remoteBrowseId?.takeIf(String::isNotBlank) }?.let { return it }
        val wantedNames = song.artist.split(',', '&').map(String::trim).filter(String::isNotBlank)
        _searchResultsArtists.value.firstOrNull { artist ->
            wantedNames.any { it.equals(artist.name, ignoreCase = true) }
        }?.let { return it.browseId }
        val primaryName = wantedNames.firstOrNull() ?: return null
        val region = normalizedRegion(runCatching { userPrefs.userRegionFlow.first() }.getOrDefault("IN"))
        return withTimeoutOrNull(8_000L) {
            repository.searchYouTubeMusicStructured(primaryName, region)
                .artists
                .firstOrNull { it.name.equals(primaryName, ignoreCase = true) }
                ?.browseId
        }
    }
}
