package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.database.AudiusFavoriteDao
import com.theveloper.pixelplay.data.database.OnlineSongCacheDao
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
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
    fun updateDailyMix(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            val favoriteIds = runCatching { favoriteSongIdsFlow.first() }.getOrDefault(emptySet())
            val region = runCatching { userPreferencesRepository.userRegionFlow.first() }
                .getOrDefault("IN")
                .ifBlank { "IN" }

            // 1. Gather online favorites
            val onlineFavorites = runCatching {
                audiusFavoriteDao.getAllFavorites().first().map { it.toSong() }
            }.getOrDefault(emptyList())

            // Imported Spotify/YouTube playlists and YouTube listening history become taste signals.
            val syncedAccountSongs = runCatching {
                onlineSongCacheDao.observeAll().first().map { it.toSong() }
            }.getOrDefault(emptyList())

            // The New Releases feed is a real YouTube Music catalog browse, not a chart alias.
            val latestReleases = runCatching {
                onlineMusicRepository.getLatestReleases(region)
            }.getOrDefault(emptyList())
            val trending = runCatching {
                onlineMusicRepository.getTrendingTracks(region)
            }.getOrDefault(emptyList())
            val loggedOutDiscovery = (latestReleases + trending).distinctBy { it.id }

            // Account history/playlists and explicit favorites are the primary taste signals.
            val tasteCandidates = (syncedAccountSongs + onlineFavorites).distinctBy { it.id }
            val preferredArtists = runCatching { userPreferencesRepository.preferredArtists.first() }
                .getOrDefault(emptySet())
            val preferredGenres = runCatching { userPreferencesRepository.preferredGenres.first() }
                .getOrDefault(emptySet())
            val blockedArtists = runCatching { userPreferencesRepository.blockedArtists.first() }
                .getOrDefault(emptySet()).mapTo(mutableSetOf()) { it.trim().lowercase() }
            
            val isNotBlocked = { song: Song -> 
                song.displayArtist.trim().lowercase() !in blockedArtists 
            }

            _latestReleaseSongs.value = personalizeLatestReleases(
                releases = latestReleases.filter(isNotBlocked),
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

                val mix = dailyMixManager.generateDailyMix(candidateSongs, favoriteIds)
                _dailyMixSongs.value = mix.toImmutableList()
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

                val yourMix = dailyMixManager.generateYourMix(candidateSongs, favoriteIds)
                _yourMixSongs.value = yourMix.toImmutableList()
                userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })

                val daySeed = java.time.LocalDate.now().toEpochDay()
                _quickPickSongs.value = dailyMixManager
                    .getTopCandidatesForAi(candidateSongs, favoriteIds, limit = 45)
                    .shuffled(kotlin.random.Random(daySeed))
                    .take(15)
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
                }
            }
        }
    }

    fun forceUpdate(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            updateDailyMix(favoriteSongIdsFlow)
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    fun checkAndUpdateIfNeeded(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
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
