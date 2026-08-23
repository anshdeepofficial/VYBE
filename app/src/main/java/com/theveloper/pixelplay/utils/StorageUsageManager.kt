package com.theveloper.pixelplay.utils

import android.content.Context
import coil.imageLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StorageUsageSnapshot(
    val imageCacheBytes: Long,
    val downloadBytes: Long,
    val otherAppDataBytes: Long,
    val totalAppBytes: Long,
)

/** Calculates only VYBE-owned private storage and never exposes private file paths. */
object StorageUsageManager {
    private const val DOWNLOADS_DIR = "pixel_downloads"

    suspend fun calculate(context: Context): StorageUsageSnapshot = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val albumArt = File(appContext.filesDir, "album_art")
        val coilImages = File(appContext.cacheDir, "image_cache")
        val downloads = File(appContext.filesDir, DOWNLOADS_DIR)
        val imageBytes = sizeOf(albumArt) + sizeOf(coilImages)
        val downloadBytes = sizeOf(downloads)
        val dataRoot = File(appContext.applicationInfo.dataDir)
        val internalBytes = sizeOf(dataRoot)
        val externalCacheBytes = appContext.externalCacheDir
            ?.takeUnless { it.absolutePath.startsWith(dataRoot.absolutePath) }
            ?.let(::sizeOf)
            ?: 0L
        val total = internalBytes + externalCacheBytes
        StorageUsageSnapshot(
            imageCacheBytes = imageBytes,
            downloadBytes = downloadBytes,
            otherAppDataBytes = (total - imageBytes - downloadBytes).coerceAtLeast(0L),
            totalAppBytes = total,
        )
    }

    suspend fun clearImageCache(context: Context): StorageUsageSnapshot = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        AlbumArtCacheManager.clearAllCache(appContext)
        deleteChildren(File(appContext.cacheDir, "image_cache"))
        appContext.imageLoader.memoryCache?.clear()
        calculate(appContext)
    }

    private fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf(::sizeOf) ?: 0L
    }

    private fun deleteChildren(directory: File) {
        val resolvedRoot = runCatching { directory.canonicalFile }.getOrNull() ?: return
        if (!resolvedRoot.exists() || !resolvedRoot.isDirectory) return
        resolvedRoot.listFiles()?.forEach { child ->
            if (child.isDirectory) deleteChildren(child)
            child.delete()
        }
    }
}
