import re

with open('app/src/main/java/com/theveloper/pixelplay/data/repository/OnlineMusicRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Insert filterAndPrioritizeRecommendations
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

# 2. Replace getFastPersonalizedDiscovery
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

content = re.sub(r'suspend fun getFastPersonalizedDiscovery.*?suspend fun getTrendingTracks', new_fast_discovery + '\n\n    suspend fun getTrendingTracks', content, flags=re.DOTALL)

# 3. Fix getTrendingTracks
new_trending = '''suspend fun getTrendingTracks(region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        val tracks = youTubeEngine.getTrendingTracks(normalizedRegion(region))
        val rawList = tracks.map { it.toSong() }
        filterAndPrioritizeRecommendations(rawList)
    }'''

content = re.sub(r'suspend fun getTrendingTracks.*?suspend fun getLatestReleases', new_trending + '\n\n    suspend fun getLatestReleases', content, flags=re.DOTALL)

# 4. Fix getLatestReleases
new_releases = '''suspend fun getLatestReleases(region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        val tracks = youTubeEngine.getLatestReleases(normalizedRegion(region))
        val rawList = tracks.map { it.toSong() }
        filterAndPrioritizeRecommendations(rawList)
    }'''

content = re.sub(r'suspend fun getLatestReleases.*?(?=\s+/\*\* Returns a seed-first continuation queue)', new_releases, content, flags=re.DOTALL)

# 5. Fix getAutoplayQueue
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

content = re.sub(r'suspend fun getAutoplayQueue.*?fun invalidateStreamUrl', new_autoplay + '\n\n    fun invalidateStreamUrl', content, flags=re.DOTALL)

with open('app/src/main/java/com/theveloper/pixelplay/data/repository/OnlineMusicRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)
