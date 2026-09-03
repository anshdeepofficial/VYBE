package com.theveloper.pixelplay.data.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import com.theveloper.pixelplay.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackRecentCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val onlineMusicRepository: OnlineMusicRepository,
    @AppScope private val appScope: CoroutineScope
) {
    companion object {
        private const val TAG = "PlaybackRecentCache"
        private const val MAX_CACHED_SONGS = 10
        private const val CACHE_DIR_NAME = "playback_audio_cache"
        private const val METADATA_FILE = "recent_10_playback_cache.json"
    }

    private val gson = Gson()
    private val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }
    private val metadataFile = File(context.filesDir, METADATA_FILE)
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs.asStateFlow()

    init {
        loadPersistedCache()
    }

    private fun loadPersistedCache() {
        try {
            if (metadataFile.exists()) {
                val json = metadataFile.readText()
                val listType = object : TypeToken<List<Song>>() {}.type
                val songs: List<Song> = gson.fromJson(json, listType) ?: emptyList()
                val updatedSongs = songs.take(MAX_CACHED_SONGS).map { song ->
                    val cleanId = cleanSongId(song.id)
                    val audioFile = File(cacheDir, "$cleanId.m4a")
                    if (audioFile.exists() && audioFile.length() > 10_000L) {
                        song.copy(path = audioFile.absolutePath, contentUriString = audioFile.absolutePath)
                    } else {
                        song
                    }
                }
                _cachedSongs.value = updatedSongs
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load persisted recent cache")
        }
    }

    fun cleanSongId(songId: String): String {
        return songId
            .removePrefix("yt://")
            .removePrefix("yt_")
            .removePrefix("saavn://")
            .removePrefix("saavn_")
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    fun getCachedAudioFile(rawIdOrUri: String): File? {
        val cleanId = cleanSongId(rawIdOrUri)
        val file = File(cacheDir, "$cleanId.m4a")
        return if (file.exists() && file.length() > 10_000L) file else null
    }

    fun onSongPlayed(song: Song) {
        appScope.launch(Dispatchers.IO) {
            try {
                val cleanId = cleanSongId(song.id)
                val audioFile = File(cacheDir, "$cleanId.m4a")
                val isAlreadyLocal = audioFile.exists() && audioFile.length() > 10_000L
                val effectiveSong = if (isAlreadyLocal) {
                    song.copy(path = audioFile.absolutePath, contentUriString = audioFile.absolutePath)
                } else song

                // Update in-memory list (latest at index 0, max 10)
                _cachedSongs.update { current ->
                    val withoutCurrent = current.filterNot { it.id == song.id }
                    (listOf(effectiveSong) + withoutCurrent).take(MAX_CACHED_SONGS)
                }
                persistCache()

                // If not downloaded yet and is online song, start caching audio in background
                val isOnline = song.id.startsWith("yt_") || song.contentUriString.startsWith("yt") ||
                        song.id.startsWith("saavn_") || song.contentUriString.startsWith("saavn")
                if (!isAlreadyLocal && isOnline) {
                    cacheAudioStream(song)
                }

                // Prune any files that are no longer in top 10
                pruneOldFiles()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error updating played song in cache: %s", song.title)
            }
        }
    }

    private fun cacheAudioStream(song: Song) {
        val cleanId = cleanSongId(song.id)
        if (activeDownloadJobs[cleanId]?.isActive == true) return

        val job = appScope.launch(Dispatchers.IO) {
            try {
                val targetFile = File(cacheDir, "$cleanId.m4a")
                if (targetFile.exists() && targetFile.length() > 10_000L) return@launch

                val streamUrl = onlineMusicRepository.resolveFreshDownloadUrl(song)
                    ?: onlineMusicRepository.resolvePlaybackUrl(song)

                if (!streamUrl.isNullOrBlank()) {
                    val request = Request.Builder()
                        .url(streamUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", "https://music.youtube.com/")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            val tempFile = File(cacheDir, "$cleanId.m4a.tmp")
                            FileOutputStream(tempFile).use { output ->
                                body.byteStream().copyTo(output)
                            }
                            if (tempFile.exists() && tempFile.length() > 10_000L) {
                                if (targetFile.exists()) targetFile.delete()
                                tempFile.renameTo(targetFile)
                                // Update song in cache with local path
                                _cachedSongs.update { list ->
                                    list.map { item ->
                                        if (item.id == song.id) {
                                            item.copy(path = targetFile.absolutePath, contentUriString = targetFile.absolutePath)
                                        } else item
                                    }
                                }
                                persistCache()
                                Timber.tag(TAG).d("Successfully cached audio for %s to %s", song.title, targetFile.absolutePath)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to cache audio stream for %s", song.id)
            } finally {
                activeDownloadJobs.remove(cleanId)
            }
        }
        activeDownloadJobs[cleanId] = job
    }

    private fun pruneOldFiles() {
        val keepCleanIds = _cachedSongs.value.map { cleanSongId(it.id) }.toSet()
        val files = cacheDir.listFiles() ?: return
        for (file in files) {
            val name = file.name
            if (name.endsWith(".tmp")) {
                file.delete()
            } else if (name.endsWith(".m4a")) {
                val cleanId = name.removeSuffix(".m4a")
                if (cleanId !in keepCleanIds) {
                    file.delete()
                    Timber.tag(TAG).d("Pruned old cached audio file: %s", name)
                }
            }
        }
    }

    private fun persistCache() {
        try {
            val json = gson.toJson(_cachedSongs.value)
            metadataFile.writeText(json)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to persist recent cache")
        }
    }
}
