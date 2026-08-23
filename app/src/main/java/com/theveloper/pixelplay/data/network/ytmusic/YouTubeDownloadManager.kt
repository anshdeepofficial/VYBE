package com.theveloper.pixelplay.data.network.ytmusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.AudiusFavoriteDao
import com.theveloper.pixelplay.data.database.AudiusFavoriteEntity
import com.theveloper.pixelplay.data.database.DownloadedSongDao
import com.theveloper.pixelplay.data.database.DownloadedSongEntity
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import com.theveloper.pixelplay.di.AppScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface YouTubeDownloadManagerEntryPoint {
    fun youtubeDownloadManager(): YouTubeDownloadManager
}

enum class SongDownloadStatus { PREPARING, DOWNLOADING, FAILED }

data class SongDownloadProgress(
    val song: Song,
    val percent: Int = 0,
    val status: SongDownloadStatus = SongDownloadStatus.PREPARING,
)

@Singleton
class YouTubeDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val onlineMusicRepository: OnlineMusicRepository,
    private val downloadedSongDao: DownloadedSongDao,
    private val audiusFavoriteDao: AudiusFavoriteDao,
    @AppScope private val appScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "YouTubeDownloadManager"
        private const val DOWNLOADS_DIR_NAME = "pixel_downloads"
        private const val DOWNLOAD_CHANNEL_ID = "vybe_song_downloads_silent"

        fun fromContext(context: Context): YouTubeDownloadManager {
            return EntryPointAccessors.fromApplication(
                context.applicationContext,
                YouTubeDownloadManagerEntryPoint::class.java
            ).youtubeDownloadManager()
        }
    }

    private fun getDownloadsDirectory(): File {
        val dir = File(context.filesDir, DOWNLOADS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        // Write .nomedia so system scanner and external apps cannot view or scan these private downloads
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            try {
                noMedia.createNewFile()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create .nomedia file: ${e.message}")
            }
        }
        return dir
    }

    fun isDownloaded(songId: String): Flow<Boolean> {
        return downloadedSongDao.isSongDownloaded(songId)
    }

    fun getDownloadedSongs(): Flow<List<Song>> {
        return downloadedSongDao.getAllDownloadedSongs().map { list ->
            list.map { it.toSong() }
        }
    }

    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private val _downloadProgress = MutableStateFlow<Map<String, SongDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, SongDownloadProgress>> = _downloadProgress.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    DOWNLOAD_CHANNEL_ID,
                    "Music downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Silent offline music download progress"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
    }

    /**
     * Starts a download in the application scope, so closing a menu or leaving a screen
     * cannot cancel it. Downloaded files have no expiry and remain until explicitly deleted.
     */
    fun enqueueDownload(song: Song): Boolean = synchronized(activeDownloads) {
        if (activeDownloads[song.id]?.isActive == true) {
            Toast.makeText(context, "Download already in progress", Toast.LENGTH_SHORT).show()
            return@synchronized false
        }
        publishProgress(song, 0, SongDownloadStatus.PREPARING)
        activeDownloads[song.id] = appScope.launch {
            try {
                val completed = downloadSong(song) { percent ->
                    publishProgress(song, percent, SongDownloadStatus.DOWNLOADING)
                }
                if (completed) {
                    showCompletedNotification(song)
                    _downloadProgress.update { it - song.id }
                } else {
                    publishProgress(song, _downloadProgress.value[song.id]?.percent ?: 0, SongDownloadStatus.FAILED)
                }
            } finally {
                activeDownloads.remove(song.id)
            }
        }
        true
    }

    private fun notificationId(songId: String): Int = 24_000 + (songId.hashCode() and 0x3FFF)

    private fun publishProgress(song: Song, percent: Int, status: SongDownloadStatus) {
        val safePercent = percent.coerceIn(0, 100)
        val previous = _downloadProgress.value[song.id]
        if (previous?.percent == safePercent && previous.status == status) return
        _downloadProgress.update {
            it + (song.id to SongDownloadProgress(song, safePercent, status))
        }
        val preparing = status == SongDownloadStatus.PREPARING
        val failed = status == SongDownloadStatus.FAILED
        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle(song.title)
            .setContentText(
                when {
                    failed -> "Download failed — tap Download to retry"
                    preparing -> "Preparing download…"
                    else -> "Downloading $safePercent%"
                }
            )
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(!failed)
            .setAutoCancel(failed)
            .setProgress(100, safePercent, preparing)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(song.id), notification) }
    }

    private fun showCompletedNotification(song: Song) {
        val notification = NotificationCompat.Builder(context, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle(song.title)
            .setContentText("Downloaded for offline playback")
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(song.id), notification) }
    }

    suspend fun downloadSong(song: Song, onProgress: ((Int) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Starting download: \"${song.title}\"...", Toast.LENGTH_SHORT).show()
            }

            val dir = getDownloadsDirectory()
            val safeFileName = "${song.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.m4a"
            val targetFile = File(dir, safeFileName)
            val tempFile = File(dir, "${safeFileName}.tmp")

            tempFile.delete()
            val isProviderTrack = song.id.startsWith("yt_") || song.id.startsWith("saavn_") ||
                song.contentUriString.startsWith("yt://") || song.contentUriString.startsWith("saavn://")
            var transferCode = -1
            for (attempt in 0..1) {
                val streamUrl = if (isProviderTrack) {
                    if (attempt > 0) onlineMusicRepository.invalidateStreamUrl(song.id)
                    onlineMusicRepository.resolveFreshDownloadUrl(song)
                } else {
                    song.path.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        ?: onlineMusicRepository.resolvePlaybackUrl(song)
                }
                if (streamUrl.isNullOrBlank()) continue
                tempFile.delete()
                transferCode = transferStream(streamUrl, tempFile, onProgress)
                if (transferCode in 200..299 && tempFile.length() > 0L) break
                val retryable = transferCode == 401 || transferCode == 403 || transferCode == 410 || transferCode >= 500
                if (!retryable) break
            }
            if (transferCode !in 200..299 || !tempFile.exists() || tempFile.length() == 0L) {
                withContext(Dispatchers.Main) {
                    val detail = transferCode.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
                    Toast.makeText(context, "Download source failed$detail", Toast.LENGTH_SHORT).show()
                }
                return@withContext false
            }

            if (targetFile.exists() && !targetFile.delete()) {
                throw IllegalStateException("Could not replace the existing download")
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw IllegalStateException("Could not save the downloaded audio")
            }

            // Save record in downloaded database
            val entity = DownloadedSongEntity(
                id = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album.ifBlank { "Downloads" },
                albumArtUrl = song.albumArtUriString,
                duration = song.duration,
                localFilePath = targetFile.absolutePath,
                mimeType = "audio/mp4",
                bitrate = song.bitrate?.takeIf { it > 0 } ?: 256,
                downloadedAt = System.currentTimeMillis()
            )
            downloadedSongDao.insert(entity)

            // Also keep in favorites for instant playlist resolution
            audiusFavoriteDao.insert(
                AudiusFavoriteEntity(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    albumArtUrl = song.albumArtUriString,
                    duration = song.duration,
                    streamUrl = targetFile.absolutePath
                )
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Downloaded: \"${song.title}\"", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    private fun transferStream(
        streamUrl: String,
        tempFile: File,
        onProgress: ((Int) -> Unit)?,
    ): Int {
        val request = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return response.code
            val body = response.body
            val totalBytes = body.contentLength()
            onProgress?.invoke(0)
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalRead = 0L
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0L) {
                            onProgress?.invoke(((totalRead * 100L) / totalBytes).toInt())
                        }
                    }
                    output.flush()
                }
            }
            return response.code
        }
    }

    suspend fun deleteDownload(songId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val song = downloadedSongDao.getDownloadedSongById(songId)
            if (song != null) {
                val file = File(song.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                downloadedSongDao.delete(songId)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Delete download error: ${e.message}")
            false
        }
    }
}
