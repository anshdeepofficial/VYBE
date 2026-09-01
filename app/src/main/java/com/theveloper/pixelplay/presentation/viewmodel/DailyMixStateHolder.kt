package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.database.AudiusFavoriteDao
import com.theveloper.pixelplay.data.database.OnlineSongCacheDao
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.recommendation.RecommendationProfile
import com.theveloper.pixelplay.data.recommendation.RecommendationSurface
import com.theveloper.pixelplay.data.recommendation.UnifiedRecommendationEngine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Daily Mix and Your Mix state with an online YouTube Music ecosystem.
 */
@Singleton
class DailyMixStateHolder @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val onlineMusicRepository: OnlineMusicRepository,
    private val audiusFavoriteDao: AudiusFavoriteDao,
    private val onlineSongCacheDao: OnlineSongCacheDao,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val recommendationEngine: UnifiedRecommendationEngine,
) {
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null

        private val _isRefreshing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRefreshing: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRefreshing

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    private val _latestReleaseSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val latestReleaseSongs: StateFlow<ImmutableList<Song>> = _latestReleaseSongs.asStateFlow()

    private val _quickPickSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val quickPickSongs: StateFlow<ImmutableList<Song>> = _quickPickSongs.asStateFlow()

    private val _trendingSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val trendingSongs: StateFlow<ImmutableList<Song>> = _trendingSongs.asStateFlow()

    private val _topMoods = MutableStateFlow<ImmutableList<String>>(persistentListOf("Chill", "Happy", "Workout", "Focus", "Romantic", "Sad", "Party", "Relax"))
    val topMoods: StateFlow<ImmutableList<String>> = _topMoods.asStateFlow()

    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            currentList.filterNot { it.id == songId }.toImmutableList()
        }
    }

    /**
     * Update the daily mix and your mix with online music ecosystem.
     */
    fun updateDailyMix(
        favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>,
        showRefreshIndicator: Boolean = false,
    ) {
        updateJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            _isRefreshing.value = showRefreshIndicator
            if (userPreferencesRepository.dataSaverEnabledFlow.first()) {
                // Keep the cache-first Home snapshot already loaded by loadPersistedDailyMix().
                // Discovery, charts and release radar are nonessential while saving data.
                _isRefreshing.value = false
                return@launch
            }
            val favoriteIds = runCatching { favoriteSongIdsFlow.first() }.getOrDefault(emptySet())
            val region = runCatching { userPreferencesRepository.userRegionFlow.first() }
                .getOrDefault("IN")
                .ifBlank { "IN" }

            // 1. Gather online favorites
            val onlineFavorites = runCatching {
                audiusFavoriteDao.getAllFavorites().first().map { it.toSong() }
            }.getOrDefault(emptyList())

            // The online cache also contains search/fetch results, so it must never be treated as
            // proof that the user likes every cached song.
            val syncedAccountSongs = runCatching {
                onlineSongCacheDao.observeAll().first().map { it.toSong() }
            }.getOrDefault(emptyList())

            val recentHistorySongs = runCatching {
                playbackStatsRepository.loadPlaybackHistory(limit = 250)
                    .mapNotNull { entry -> entry.track?.toSong(entry.songId) }
                    .distinctBy { it.id }
            }.getOrDefault(emptyList())
            val localFavoriteSongs = runCatching {
                musicRepository.getSongsByIds(favoriteIds.toList()).first()
            }.getOrDefault(emptyList())
            val cachedFavorites = syncedAccountSongs.filter { it.id in favoriteIds }

            // The New Releases feed is a real YouTube Music catalog browse, not a chart alias.
            val latestReleases = runCatching {
                onlineMusicRepository.getLatestReleases(region)
            }.getOrDefault(emptyList())
            val trending = runCatching {
                onlineMusicRepository.getTrendingTracks(region)
            }.getOrDefault(emptyList())
            _trendingSongs.value = trending.distinctBy { it.id }.take(30).toImmutableList()
            val loggedOutDiscovery = (latestReleases + trending).distinctBy { it.id }

            // Account history/playlists and explicit favorites are the primary taste signals.
            val tasteCandidates = (
                recentHistorySongs + localFavoriteSongs + cachedFavorites + onlineFavorites
            ).distinctBy { it.id }
            val preferredArtists = runCatching { userPreferencesRepository.preferredArtists.first() }
                .getOrDefault(emptySet())
            val preferredGenres = runCatching { userPreferencesRepository.preferredGenres.first() }
                .getOrDefault(emptySet())
            val blockedArtists = runCatching { userPreferencesRepository.blockedArtists.first() }
                .getOrDefault(emptySet()).mapTo(mutableSetOf()) { it.trim().lowercase() }
            
            val isNotBlocked = { song: Song -> 
                song.displayArtist.trim().lowercase() !in blockedArtists 
            }

            val verifiedRecentReleases = latestReleases
                .filter(isNotBlocked)
                .filter(::isWithinReleaseRadarWindow)
            _latestReleaseSongs.value = personalizeLatestReleases(
                releases = verifiedRecentReleases,
                tasteSongs = tasteCandidates,
                preferredArtists = preferredArtists,
                preferredGenres = preferredGenres,
            ).sortedByDescending { it.releaseDateEpochMillis }.take(30).toImmutableList()

            // Latest releases are used only for cold-start discovery, never mixed into an
            // established profile as generic chart filler.
            val candidateSongs = tasteCandidates.filter(isNotBlocked).ifEmpty { 
                loggedOutDiscovery.filter(isNotBlocked) 
            }

            if (candidateSongs.isNotEmpty()) {
                val moodToGenres = mapOf(
                    "Chill" to listOf("lo-fi", "chill", "acoustic", "indie", "ambient"),
                    "Happy" to listOf("pop", "dance", "upbeat", "happy"),
                    "Workout" to listOf("rock", "metal", "hip hop", "electronic", "dance", "workout"),
                    "Focus" to listOf("classical", "jazz", "ambient", "instrumental", "focus"),
                    "Romantic" to listOf("r&b", "soul", "romantic", "love"),
                    "Sad" to listOf("blues", "sad", "acoustic", "emo"),
                    "Party" to listOf("pop", "dance", "electronic", "party", "club"),
                    "Relax" to listOf("ambient", "classical", "lo-fi", "relax")
                )
                
                val genreAffinity = tasteCandidates
                    .mapNotNull { it.genre?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
                    .groupingBy { it }
                    .eachCount()
                    
                val sortedMoods = moodToGenres.keys.toList().sortedByDescending { mood ->
                    moodToGenres[mood]?.sumOf { genre -> genreAffinity[genre] ?: 0 } ?: 0
                }
                _topMoods.value = sortedMoods.toImmutableList()

                val recommendationProfile = RecommendationProfile.fromTaste(
                    songs = tasteCandidates,
                    preferredArtists = preferredArtists,
                    preferredGenres = preferredGenres,
                    blockedArtists = blockedArtists,
                )
                val rankedCandidates = recommendationEngine.rank(
                    candidates = candidateSongs,
                    profile = recommendationProfile,
                    surface = RecommendationSurface.HOME,
                )
                val mix = dailyMixManager.generateDailyMix(rankedCandidates, favoriteIds)
                _dailyMixSongs.value = mix.toImmutableList()
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

                val yourMix = dailyMixManager.generateYourMix(rankedCandidates, favoriteIds)
                _yourMixSongs.value = yourMix.toImmutableList()
                userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })

                _quickPickSongs.value = dailyMixManager
                    .getTopCandidatesForAi(rankedCandidates, favoriteIds, limit = 45)
                    .take(10)
                    .toImmutableList()
            } else {
                _yourMixSongs.value = persistentListOf()
                _dailyMixSongs.value = persistentListOf()
                _quickPickSongs.value = persistentListOf()
            }
            _isRefreshing.value = false
        }
    }

    fun loadPersistedDailyMix() {
        scope?.launch {
            val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
            if (dailyMixIds.isNotEmpty() && _dailyMixSongs.value.isEmpty()) {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(dailyMixIds).first()
                }
                if (songs.isNotEmpty()) {
                    val songMap = songs.associateBy { it.id }
                    val orderedSongs = dailyMixIds.mapNotNull { songMap[it] }
                    _dailyMixSongs.value = orderedSongs.toImmutableList()
                }
            }
        }

        scope?.launch {
            val yourMixIds = userPreferencesRepository.yourMixSongIdsFlow.first()
            if (yourMixIds.isNotEmpty() && _yourMixSongs.value.isEmpty()) {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIds(yourMixIds).first()
                }
                if (songs.isNotEmpty()) {
                    val songMap = songs.associateBy { it.id }
                    val orderedSongs = yourMixIds.mapNotNull { songMap[it] }
                    _yourMixSongs.value = orderedSongs.toImmutableList()
                    if (_quickPickSongs.value.isEmpty()) {
                        // Instant cache-first Home content while the daily online refresh runs
                        // silently in the background.
                        _quickPickSongs.value = orderedSongs.take(10).toImmutableList()
                    }
                }
            }
        }
    }

    fun forceUpdate(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            if (userPreferencesRepository.dataSaverEnabledFlow.first()) return@launch
            updateDailyMix(favoriteSongIdsFlow, showRefreshIndicator = true)
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    fun checkAndUpdateIfNeeded(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            if (userPreferencesRepository.dataSaverEnabledFlow.first()) return@launch
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val lastUpdateDay = Calendar.getInstance().apply {
                timeInMillis = lastUpdate
            }.get(Calendar.DAY_OF_YEAR)

            if (today != lastUpdateDay) {
                updateDailyMix(favoriteSongIdsFlow)
                userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
            }
        }
    }

    fun setDailyMixSongs(songs: List<Song>) {
        _dailyMixSongs.value = songs.toImmutableList()
        scope?.launch {
            userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
        }
    }

    private fun personalizeLatestReleases(
        releases: List<Song>,
        tasteSongs: List<Song>,
        preferredArtists: Set<String>,
        preferredGenres: Set<String>,
    ): List<Song> {
        if (releases.isEmpty()) return emptyList()

        val artistAffinity = tasteSongs
            .groupingBy { it.displayArtist.trim().lowercase() }
            .eachCount()
        val genreAffinity = tasteSongs
            .mapNotNull { it.genre?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
        val preferredArtistKeys = preferredArtists.mapTo(mutableSetOf()) { it.trim().lowercase() }
        val preferredGenreKeys = preferredGenres.mapTo(mutableSetOf()) { it.trim().lowercase() }

        if (artistAffinity.isEmpty() && genreAffinity.isEmpty() &&
            preferredArtistKeys.isEmpty() && preferredGenreKeys.isEmpty()
        ) {
            return releases.distinctBy { it.id }
        }

        val providerOrder = releases.withIndex().associate { it.value.id to it.index }
        return releases
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Song> { song ->
                    val artistKey = song.displayArtist.trim().lowercase()
                    val genreKey = song.genre?.trim()?.lowercase()
                    (artistAffinity[artistKey] ?: 0) * 5 +
                        (genreAffinity[genreKey] ?: 0) * 2 +
                        (if (artistKey in preferredArtistKeys) 8 else 0) +
                        (if (genreKey != null && genreKey in preferredGenreKeys) 4 else 0)
                }.thenBy { providerOrder[it.id] ?: Int.MAX_VALUE }
            )
    }

    private fun isWithinReleaseRadarWindow(song: Song): Boolean {
        if (song.releaseDateEpochMillis <= 0L) return false
        val zone = java.time.ZoneId.systemDefault()
        val date = java.time.Instant.ofEpochMilli(song.releaseDateEpochMillis).atZone(zone).toLocalDate()
        val today = java.time.LocalDate.now(zone)
        return !date.isBefore(today.minusDays(29)) && !date.isAfter(today)
    }

    suspend fun getCandidatePool(
        allSongs: List<Song>,
        favoriteIds: Set<String>,
        maxSize: Int = 100
    ): List<Song> {
        return dailyMixManager.generateDailyMix(allSongs, favoriteIds, maxSize)
    }

    fun onCleared() {
        updateJob?.cancel()
        scope = null
    }
}
