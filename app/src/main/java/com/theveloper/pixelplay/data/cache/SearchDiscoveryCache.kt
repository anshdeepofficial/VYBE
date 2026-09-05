package com.theveloper.pixelplay.data.cache

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchDiscoveryCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class DiscoverySnapshot(
        val bestForYouTracks: List<Song> = emptyList(),
        val aiRecommendations: List<Song> = emptyList(),
        val latestReleases: List<Song> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    )

    private val cacheFile = File(context.filesDir, "search_discovery_snapshot.json")

    @Volatile
    private var memorySnapshot: DiscoverySnapshot? = null

    init {
        // Crucial: Clean up any legacy corrupt file from previous versions to eliminate LinkedTreeMap ClassCastException
        runCatching {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }

    fun get(): DiscoverySnapshot? = memorySnapshot

    fun put(snapshot: DiscoverySnapshot) {
        memorySnapshot = snapshot
    }
}
