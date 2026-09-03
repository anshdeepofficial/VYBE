package com.theveloper.pixelplay.data.cache

import android.content.Context
import com.google.gson.Gson
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
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

    private val gson = Gson()
    private val cacheFile = File(context.filesDir, "search_discovery_snapshot.json")

    @Volatile
    private var memorySnapshot: DiscoverySnapshot? = null

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val snapshot = gson.fromJson(json, DiscoverySnapshot::class.java)
                if (snapshot != null && (snapshot.bestForYouTracks.isNotEmpty() || snapshot.aiRecommendations.isNotEmpty())) {
                    memorySnapshot = snapshot
                }
            }
        } catch (e: Exception) {
            Timber.tag("SearchDiscoveryCache").w(e, "Failed to load discovery snapshot from disk")
        }
    }

    fun get(): DiscoverySnapshot? = memorySnapshot

    fun put(snapshot: DiscoverySnapshot) {
        memorySnapshot = snapshot
        try {
            val json = gson.toJson(snapshot)
            cacheFile.writeText(json)
        } catch (e: Exception) {
            Timber.tag("SearchDiscoveryCache").w(e, "Failed to write discovery snapshot to disk")
        }
    }
}
