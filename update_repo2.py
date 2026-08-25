import re

with open('app/src/main/java/com/theveloper/pixelplay/data/repository/OnlineMusicRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

filter_code = '''
    private suspend fun filterAndPrioritizeRecommendations(songs: List<Song>): List<Song> {
        val blocked = userPreferencesRepository.blockedArtists.first().map { it.lowercase() }
        val preferred = userPreferencesRepository.preferredArtists.first().map { it.lowercase() }
        
        return songs.filter { song ->
            val songArtist = song.artist.lowercase()
            blocked.none { songArtist.contains(it) }
        }.sortedByDescending { song ->
            val songArtist = song.artist.lowercase()
            if (preferred.any { songArtist.contains(it) }) 1 else 0
        }
    }

'''
content = content.replace('suspend fun getFastPersonalizedDiscovery', filter_code + 'suspend fun getFastPersonalizedDiscovery')

old_fast = '''suspend fun getFastPersonalizedDiscovery(interestLabels: List<String>): List<Song> = coroutineScope {
        val queries = interestLabels
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .take(3)
            .map { "$it songs" }
            .ifEmpty { listOf("Trending Punjabi Hindi songs 2026") }

        queries.map { query ->
            async(Dispatchers.IO) { runCatching { saavnEngine.searchSongs(query) }.getOrDefault(emptyList()) }
        }.map { it.await() }
            .flatten()
            .filter(::isUsefulDiscoverySong)
            .distinctBy { it.id }
            .take(30)
    }'''

new_fast_discovery = '''suspend fun getFastPersonalizedDiscovery(interestLabels: List<String>): List<Song> = coroutineScope {
        val moodToBrowseId = mapOf(
            "chill" to "FEmusic_moods_and_genres_chill",
            "happy" to "FEmusic_moods_and_genres_happy",
            "workout" to "FEmusic_moods_and_genres_workout",
            "focus" to "FEmusic_moods_and_genres_focus",
            "romantic" to "FEmusic_moods_and_genres_romance",
            "sad" to "FEmusic_moods_and_genres_sad",
            "party" to "FEmusic_moods_and_genres_party",
            "relax" to "FEmusic_moods_and_genres_relax",
            "sleep" to "FEmusic_moods_and_genres_sleep"
        )

        val moods = interestLabels
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .take(3)

        val region = runCatching { userPreferencesRepository.userRegionFlow.first() }.getOrDefault("IN").ifBlank { "IN" }

        if (moods.isEmpty()) {
            return@coroutineScope getTrendingTracks(region)
        }

        val ytTracks = moods.map { mood ->
            async(Dispatchers.IO) {
                val browseId = moodToBrowseId[mood.lowercase()]
                if (browseId != null) {
                    runCatching { youTubeEngine.getMoodTracks(browseId, region) }.getOrDefault(emptyList())
                } else {
                    emptyList<com.theveloper.pixelplay.data.network.ytmusic.YouTubeTrack>()
                }
            }
        }.map { it.await() }.flatten()

        val rawList = if (ytTracks.isNotEmpty()) {
            ytTracks.map { it.toSong() }.take(50)
        } else {
            getTrendingTracks(region)
        }
        
        return@coroutineScope filterAndPrioritizeRecommendations(rawList)
    }'''

content = content.replace(old_fast, new_fast_discovery)


old_trending = '''suspend fun getTrendingTracks(region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        val tracks = youTubeEngine.getTrendingTracks(normalizedRegion(region))
        tracks.map { it.toSong() }.ifEmpty {
            saavnEngine.searchSongs("Trending Punjabi Hindi songs 2026")
                .filter(::isUsefulDiscoverySong)
        }
    }'''

new_trending = '''suspend fun getTrendingTracks(region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        val tracks = youTubeEngine.getTrendingTracks(normalizedRegion(region))
        val rawList = tracks.map { it.toSong() }
        filterAndPrioritizeRecommendations(rawList)
    }'''

content = content.replace(old_trending, new_trending)


old_releases = '''suspend fun getLatestReleases(region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        youTubeEngine.getLatestReleases(normalizedRegion(region)).map { it.toSong() }.ifEmpty {
            saavnEngine.searchSongs("Latest Punjabi Hindi releases 2026")
                .filter(::isUsefulDiscoverySong)
        }
    }'''

new_releases = '''suspend fun getLatestReleases(region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        val tracks = youTubeEngine.getLatestReleases(normalizedRegion(region))
        val rawList = tracks.map { it.toSong() }
        filterAndPrioritizeRecommendations(rawList)
    }'''
    
content = content.replace(old_releases, new_releases)


old_autoplay = '''suspend fun getAutoplayQueue(seed: Song, region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        if (seed.id.startsWith("yt_") || seed.id.startsWith("saavn_")) {
            onlineSongCacheDao.upsertAll(listOf(seed.toOnlineSongCacheEntity()))
        }
        val normalized = normalizedRegion(region)
        val providerContinuation = if (seed.id.startsWith("yt_") || seed.contentUriString.startsWith("yt://")) {
            youTubeEngine.getAutoplayTracks(seed.id, normalized).map { it.toSong() }
        } else {
            emptyList()
        }

        val relatedFallback = if (providerContinuation.size >= 5) emptyList() else {
            val albumSignal = seed.album
                .takeUnless { it.isBlank() || it.equals("YouTube Music", ignoreCase = true) }
                .orEmpty()
            val relatedQuery = listOf(seed.artist, albumSignal).filter(String::isNotBlank).joinToString(" ")
            if (relatedQuery.isBlank()) emptyList() else {
                val yt = runCatching { youTubeEngine.search(relatedQuery, normalized).map { it.toSong() } }
                    .getOrDefault(emptyList())
                val fallback = if (yt.isEmpty()) {
                    runCatching { saavnEngine.searchSongs("${seed.artist} songs") }.getOrDefault(emptyList())
                } else emptyList()
                (yt + fallback).filter(::isUsefulDiscoverySong)
            }
        }

        listOf(seed) + (providerContinuation + relatedFallback)
            .filterNot { it.id == seed.id }
            .distinctBy { it.id }
            .take(49)
    }'''

new_autoplay = '''suspend fun getAutoplayQueue(seed: Song, region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        if (seed.id.startsWith("yt_") || seed.id.startsWith("saavn_")) {
            onlineSongCacheDao.upsertAll(listOf(seed.toOnlineSongCacheEntity()))
        }
        val normalized = normalizedRegion(region)
        val providerContinuation = if (seed.id.startsWith("yt_") || seed.contentUriString.startsWith("yt://")) {
            youTubeEngine.getAutoplayTracks(seed.id, normalized).map { it.toSong() }
        } else {
            emptyList()
        }

        val relatedFallback = if (providerContinuation.size >= 5) emptyList() else {
            val albumSignal = seed.album
                .takeUnless { it.isBlank() || it.equals("YouTube Music", ignoreCase = true) }
                .orEmpty()
            val relatedQuery = listOf(seed.artist, albumSignal).filter(String::isNotBlank).joinToString(" ")
            if (relatedQuery.isBlank()) emptyList() else {
                val yt = runCatching { youTubeEngine.search(relatedQuery, normalized).map { it.toSong() } }
                    .getOrDefault(emptyList())
                yt.filter(::isUsefulDiscoverySong)
            }
        }

        val rawList = listOf(seed) + (providerContinuation + relatedFallback)
            .filterNot { it.id == seed.id }
            .distinctBy { it.id }
            .take(49)
            
        filterAndPrioritizeRecommendations(rawList)
    }'''

content = content.replace(old_autoplay, new_autoplay)

with open('app/src/main/java/com/theveloper/pixelplay/data/repository/OnlineMusicRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)
