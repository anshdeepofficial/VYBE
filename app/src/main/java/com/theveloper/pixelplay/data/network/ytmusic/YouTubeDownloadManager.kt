package com.theveloper.pixelplay.data.network.ytmusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

enum class SongDownloadStatus { PREPARING, DOWNLOADING, PAUSED, FAILED }

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
        private const val EXTRA_SONG_ID = "song_id"
        private const val ACTION_PAUSE = "com.theveloper.pixelplay.download.PAUSE"
        private const val ACTION_RESUME = "com.theveloper.pixelplay.download.RESUME"
        private const val ACTION_RETRY = "com.theveloper.pixelplay.download.RETRY"
        private const val ACTION_CANCEL = "com.theveloper.pixelplay.download.CANCEL"

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
    private val pausedDownloads = ConcurrentHashMap.newKeySet<String>()
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
    fun enqueueDownload(song: Song, resume: Boolean = false): Boolean = synchronized(activeDownloads) {
        if (activeDownloads[song.id]?.isActive == true) {
            Toast.makeText(context, "Download already in progress", Toast.LENGTH_SHORT).show()
            return@synchronized false
        }
        pausedDownloads.remove(song.id)
        publishProgress(song, if (resume) _downloadProgress.value[song.id]?.percent ?: 0 else 0, SongDownloadStatus.PREPARING)
        activeDownloads[song.id] = appScope.launch {
            try {
                val completed = downloadSong(song, resume) { percent ->
                    publishProgress(song, percent, SongDownloadStatus.DOWNLOADING)
                }
                if (completed) {
                    showCompletedNotification(song)
                    _downloadProgress.update { it - song.id }
                } else if (!pausedDownloads.contains(song.id)) {
                    publishProgress(song, _downloadProgress.value[song.id]?.percent ?: 0, SongDownloadStatus.FAILED)
                }
            } catch (_: CancellationException) {
                if (!pausedDownloads.contains(song.id)) _downloadProgress.update { it - song.id }
            } finally {
                activeDownloads.remove(song.id)
            }
        }
        true
    }

    fun pauseDownload(songId: String) {
        val progress = _downloadProgress.value[songId] ?: return
        pausedDownloads.add(songId)
        activeDownloads.remove(songId)?.cancel()
        publishProgress(progress.song, progress.percent, SongDownloadStatus.PAUSED)
    }

    fun resumeDownload(songId: String): Boolean {
        val progress = _downloadProgress.value[songId] ?: return false
        return enqueueDownload(progress.song, resume = true)
    }

    fun retryDownload(songId: String): Boolean {
        val progress = _downloadProgress.value[songId] ?: return false
        cancelDownload(songId)
        return enqueueDownload(progress.song)
    }

    fun cancelDownload(songId: String) {
        pausedDownloads.remove(songId)
        activeDownloads.remove(songId)?.cancel()
        val safe = songId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        File(getDownloadsDirectory(), "$safe.m4a.tmp").delete()
        _downloadProgress.update { it - songId }
        NotificationManagerCompat.from(context).cancel(notificationId(songId))
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
        val paused = status == SongDownloadStatus.PAUSED
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
            .setOngoing(!failed && !paused)
            .setAutoCancel(false)
            .setProgress(100, safePercent, preparing)
            .addAction(
                R.drawable.monochrome_player,
                if (failed) "Retry" else if (paused) "Resume" else "Pause",
                actionIntent(if (failed) ACTION_RETRY else if (paused) ACTION_RESUME else ACTION_PAUSE, song.id),
            )
            .addAction(R.drawable.monochrome_player, "Cancel", actionIntent(ACTION_CANCEL, song.id))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(song.id), notification) }
    }

    private fun actionIntent(action: String, songId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        notificationId(songId) xor action.hashCode(),
        Intent(context, DownloadActionReceiver::class.java).setAction(action).putExtra(EXTRA_SONG_ID, songId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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

    suspend fun downloadSong(
        song: Song,
        resume: Boolean = false,
        onProgress: ((Int) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Starting download: \"${song.title}\"...", Toast.LENGTH_SHORT).show()
            }

            val dir = getDownloadsDirectory()
            val safeFileName = "${song.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.m4a"
            val targetFile = File(dir, safeFileName)
            val tempFile = File(dir, "${safeFileName}.tmp")

            if (!resume) tempFile.delete()
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
                if (!resume || attempt > 0) tempFile.delete()
                transferCode = transferStream(streamUrl, tempFile, resume && attempt == 0, onProgress)
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    private suspend fun transferStream(
        streamUrl: String,
        tempFile: File,
        resume: Boolean,
        onProgress: ((Int) -> Unit)?,
    ): Int {
        val existingBytes = if (resume && tempFile.exists()) tempFile.length() else 0L
        val requestBuilder = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        if (existingBytes > 0L) requestBuilder.header("Range", "bytes=$existingBytes-")
        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return response.code
            val body = response.body
            val appending = existingBytes > 0L && response.code == 206
            val completedBytes = if (appending) existingBytes else 0L
            val totalBytes = body.contentLength().takeIf { it > 0L }?.plus(completedBytes) ?: -1L
            body.byteStream().use { input ->
                FileOutputStream(tempFile, appending).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalRead = completedBytes
                    while (true) {
                        currentCoroutineContext().ensureActive()
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

    internal fun handleNotificationAction(action: String?, songId: String) {
        when (action) {
            ACTION_PAUSE -> pauseDownload(songId)
            ACTION_RESUME -> resumeDownload(songId)
            ACTION_RETRY -> retryDownload(songId)
            ACTION_CANCEL -> cancelDownload(songId)
        }
    }
}

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val songId = intent.getStringExtra("song_id") ?: return
        YouTubeDownloadManager.fromContext(context).handleNotificationAction(intent.action, songId)
    }
}
