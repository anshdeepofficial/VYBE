package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.saavn.JioSaavnEngine
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeAlbumDetails
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeArtistProfile
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeMusicEngine
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeSearchResult
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineMusicRepository @Inject constructor(
    private val youTubeEngine: YouTubeMusicEngine,
    private val saavnEngine: JioSaavnEngine
) {
    suspend fun searchSongs(query: String, region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        youTubeEngine.search(query, region).map { it.toSong() }
    }

    suspend fun searchMusicStructured(query: String, region: String = "IN"): YouTubeSearchResult = withContext(Dispatchers.IO) {
        val youtubeResult = youTubeEngine.searchMusicStructured(query, region)
        if (youtubeResult.songs.isNotEmpty() || youtubeResult.albums.isNotEmpty() || youtubeResult.artists.isNotEmpty()) {
            youtubeResult
        } else {
            // Keep search useful during temporary YouTube Music endpoint/profile failures.
            // Playback already supports these music-only Saavn results through the same player.
            YouTubeSearchResult(songs = saavnEngine.searchSongs(query))
        }
    }

    suspend fun searchYouTubeMusicStructured(
        query: String,
        region: String = "IN"
    ): YouTubeSearchResult = withContext(Dispatchers.IO) {
        youTubeEngine.searchMusicStructured(query, normalizedRegion(region))
    }

    suspend fun searchFallbackSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        saavnEngine.searchSongs(query)
    }

    suspend fun getFastPersonalizedDiscovery(interestLabels: List<String>): List<Song> = coroutineScope {
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
    }

    suspend fun getTrendingTracks(region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        val tracks = youTubeEngine.getTrendingTracks(normalizedRegion(region))
        tracks.map { it.toSong() }.ifEmpty {
            saavnEngine.searchSongs("Trending Punjabi Hindi songs 2026")
                .filter(::isUsefulDiscoverySong)
        }
    }

    suspend fun getLatestReleases(region: String = "IN"): List<Song> = withContext(Dispatchers.IO) {
        youTubeEngine.getLatestReleases(normalizedRegion(region)).map { it.toSong() }.ifEmpty {
            saavnEngine.searchSongs("Latest Punjabi Hindi releases 2026")
                .filter(::isUsefulDiscoverySong)
        }
    }

    private fun normalizedRegion(region: String): String =
        region.takeUnless { it.isBlank() || it.equals("Global", ignoreCase = true) } ?: "IN"

    private fun isUsefulDiscoverySong(song: Song): Boolean {
        val metadata = "${song.title} ${song.artist} ${song.album}".lowercase()
        val junkPhrases = listOf(
            "global trending",
            "global headlines",
            "breaking news",
            "stress relief",
            "meditation music",
            "kids sleep",
            "sleep baby",
            "natural dreams",
        )
        return song.title.isNotBlank() && song.artist.isNotBlank() && junkPhrases.none(metadata::contains)
    }

    suspend fun getArtistProfile(browseId: String): YouTubeArtistProfile? = withContext(Dispatchers.IO) {
        youTubeEngine.getArtistProfile(browseId)
    }

    suspend fun getAlbumDetails(browseId: String): YouTubeAlbumDetails? = withContext(Dispatchers.IO) {
        youTubeEngine.getAlbumDetails(browseId)
    }

    suspend fun resolvePlaybackUrl(song: Song): String? = withContext(Dispatchers.IO) {
        if (song.path.startsWith("http://") || song.path.startsWith("https://") || song.path.startsWith("/")) {
            return@withContext song.path
        }
        if (song.id.startsWith("saavn_") || song.path.startsWith("saavn://")) {
            val saavnUrl = saavnEngine.resolveStreamByQuery("${song.title} ${song.artist}".trim())
            if (!saavnUrl.isNullOrBlank()) return@withContext saavnUrl
        }
        
        // 1. Try YouTube stream resolution
        val ytUrl = youTubeEngine.resolveStreamUrl(song.id)
        if (!ytUrl.isNullOrBlank()) return@withContext ytUrl

        // 2. Fallback to Saavn by query
        val query = "${song.title} ${song.artist}".trim()
        if (query.isNotBlank()) {
            val saavnUrl = saavnEngine.resolveStreamByQuery(query)
            if (!saavnUrl.isNullOrBlank()) return@withContext saavnUrl
        }
        null
    }

    fun resolvePlaybackUrlSync(videoId: String, title: String, artist: String, isSaavn: Boolean): String? {
        if (isSaavn) {
            val query = "$title $artist".trim()
            if (query.isNotBlank()) {
                return kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    saavnEngine.resolveStreamByQuery(query)
                }
            }
        }

        val ytUrl = youTubeEngine.resolveStreamUrlSync(videoId)
        if (!ytUrl.isNullOrBlank()) return ytUrl

        // Fallback to JioSaavn if YouTube fails
        val query = "$title $artist".trim()
        if (query.isNotBlank()) {
            return kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                saavnEngine.resolveStreamByQuery(query)
            }
        }
        return null
    }

    fun invalidateStreamUrl(videoId: String) {
        youTubeEngine.invalidateStreamUrl(videoId)
        saavnEngine.invalidateCache(videoId)
    }
}
