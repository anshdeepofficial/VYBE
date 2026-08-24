package com.theveloper.pixelplay.data.service.voice

import android.content.Context
import androidx.media3.common.MediaItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePlaybackRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onlineMusicRepository: OnlineMusicRepository,
    private val musicRepository: MusicRepository
) {
    suspend fun routeVoiceQuery(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        // 1. Search Online
        val searchResult = onlineMusicRepository.searchMusicStructured(query)

        // 2. Find Best Match
        val seedSong = findBestMatch(query, searchResult.songs)
            ?: searchResult.songs.firstOrNull()
            ?: return@withContext emptyList()

        // 3. Get Autoplay Queue
        val queue = onlineMusicRepository.getAutoplayQueue(seedSong)

        // 4. Return MediaItems
        queue.map { MediaItemBuilder.buildForExternalController(context, it) }
    }

    private fun findBestMatch(query: String, songs: List<Song>): Song? {
        if (songs.isEmpty()) return null

        val lowerQuery = query.lowercase().trim()
        
        // Exact title + exact artist match
        val exactTitleArtist = songs.find { 
            lowerQuery.contains(it.title.lowercase()) && lowerQuery.contains(it.displayArtist.lowercase())
        }
        if (exactTitleArtist != null) return exactTitleArtist

        // Exact title match
        val exactTitle = songs.find { it.title.lowercase() == lowerQuery }
        if (exactTitle != null) return exactTitle

        // Title contains query or query contains title
        val partialTitle = songs.find { 
            val lowerTitle = it.title.lowercase()
            lowerTitle.contains(lowerQuery) || lowerQuery.contains(lowerTitle)
        }
        if (partialTitle != null) return partialTitle

        return songs.firstOrNull()
    }
}
