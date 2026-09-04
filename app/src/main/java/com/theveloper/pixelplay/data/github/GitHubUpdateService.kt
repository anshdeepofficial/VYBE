package com.theveloper.pixelplay.data.github


import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.formatBytes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.theveloper.pixelplay.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

data class GitHubReleaseUpdate(
    val tagName: String,
    val title: String,
    val notes: String,
    val versionCode: Long?,
    val apkName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val apkSha256: String? = null,
)

class GitHubUpdateService {

    suspend fun checkForUpdate(
        context: Context,
        respectDismissal: Boolean = true,
    ): Result<GitHubReleaseUpdate?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val owner = BuildConfig.VYBE_GITHUB_OWNER.trim()
                val repo = BuildConfig.VYBE_GITHUB_REPO.trim()
                if (owner.isBlank() || repo.isBlank()) return@runCatching null

                // The manifest is the authoritative update source. It avoids parsing human-written
                // release notes and prevents stale/replaced GitHub assets from being offered.
                val manifest = runCatching {
                    manifestUpdate(context, owner, repo, respectDismissal)
                }.getOrNull()
                if (manifest?.update != null) return@runCatching manifest.update

                val connection = openConnection(
                    "https://api.github.com/repos/$owner/$repo/releases/latest",
                    accept = "application/vnd.github+json",
                )
                val response = connection.useResponse()
                if (response.code == HttpURLConnection.HTTP_NOT_FOUND) return@runCatching null
                check(response.code in 200..299) { "VYBE update check failed (${response.code})" }

                val release = JSONObject(response.body)
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@runCatching null
                val tag = release.optString("tag_name").trim()
                val releaseNotes = release.optString("body").trim()
                // Use Android's installed package metadata as the source of truth. BuildConfig can
                // differ from it in upgraded/split installs, which previously made VYBE offer the
                // already-installed GitHub release again.
                val releaseVersionCode = releaseVersionCode(releaseNotes)
                val installedCode = installedVersionCode(context)
                val installedName = installedVersionName(context).orEmpty()
                if (tag.isBlank()) return@runCatching null
                // Android's versionCode is the authoritative upgrade identity. Only fall back to
                // semantic versionName comparison for legacy releases that omit versionCode.
                val isActualUpgrade = if (releaseVersionCode != null) {
                    releaseVersionCode > installedCode
                } else {
                    isNewerVersion(tag, installedName)
                }
                if (!isActualUpgrade) {
                    return@runCatching null
                }

                val assets = release.optJSONArray("assets") ?: return@runCatching null
                val candidates = buildList {
                    for (index in 0 until assets.length()) {
                        val asset = assets.optJSONObject(index) ?: continue
                        val name = asset.optString("name")
                        val url = asset.optString("browser_download_url")
                        if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
                            add(Triple(name, url, asset.optLong("size", 0L)))
                        }
                    }
                }
                val selected = candidates.maxByOrNull { apkScore(it.first) } ?: return@runCatching null
                val releaseKey = versionKey(tag)
                val dismissedTag = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_DISMISSED_TAG, null)
                if (respectDismissal && versionKey(dismissedTag.orEmpty()) == releaseKey) {
                    return@runCatching null
                }
                val remindAfter = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getLong(KEY_REMIND_AFTER, 0L)
                if (respectDismissal && System.currentTimeMillis() < remindAfter) return@runCatching null

                GitHubReleaseUpdate(
                    tagName = tag,
                    title = release.optString("name").ifBlank { "VYBE $tag" },
                    notes = releaseNotes.take(MAX_NOTES_LENGTH),
                    versionCode = releaseVersionCode,
                    apkName = selected.first,
                    apkUrl = selected.second,
                    apkSizeBytes = selected.third.takeIf { it > 0L } ?: resolveRemoteFileSize(selected.second),
                    apkSha256 = null,
                )
            }
        }

    suspend fun download(
        context: Context,
        update: GitHubReleaseUpdate,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        if (update.versionCode != null && update.versionCode <= installedVersionCode(context)) {
            dismiss(context, update)
            return@withContext Result.failure(
                IllegalStateException("VYBE is already fully up to date")
            )
        }
        
        val channelId = "vybe_update_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        val notificationId = 10001
        val notificationManager = NotificationManagerCompat.from(context)
        val initialTotal = update.apkSizeBytes
        val initialText = if (initialTotal > 0L) {
            "${formatBytes(0L)} / ${formatBytes(initialTotal)} (0%)"
        } else {
            update.apkName
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle("Downloading VYBE Update")
            .setContentText(initialText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setProgress(100, 0, false)

        val directory = File(context.cacheDir, "app_updates").apply { mkdirs() }
        val target = File(directory, safeFileName(update.apkName))
        val partial = File(directory, "${target.name}.partial")
        runCatching {
            if (partial.exists()) partial.delete()

            val connection = openDownloadConnection(update.apkUrl)
            val code = connection.responseCode
            check(code in 200..299) { "Update download failed ($code)" }
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: update.apkSizeBytes
            var lastNotificationTime = 0L
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        val progress = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                        onProgress(progress, downloaded, total)

                        val now = System.currentTimeMillis()
                        if (now - lastNotificationTime >= 400L || downloaded == total) {
                            lastNotificationTime = now
                            if (total > 0L) {
                                val percent = (progress * 100).toInt().coerceIn(0, 100)
                                builder.setContentText("${formatBytes(downloaded)} / ${formatBytes(total)} ($percent%)")
                                builder.setProgress(100, percent, false)
                            } else {
                                builder.setContentText("Downloaded ${formatBytes(downloaded)}")
                                builder.setProgress(0, 0, true)
                            }
                            notificationManager.notify(notificationId, builder.build())
                        }
                    }
                }
            }
            connection.disconnect()
            check(partial.length() > 4L) { "Downloaded APK is empty" }
            if (update.apkSizeBytes > 0L) {
                check(partial.length() == update.apkSizeBytes) { "Downloaded update size does not match" }
            }
            update.apkSha256?.takeIf(String::isNotBlank)?.let { expected ->
                check(fileSha256(partial).equals(expected, ignoreCase = true)) {
                    "Downloaded update failed its integrity check"
                }
            }
            partial.inputStream().use { stream ->
                check(stream.read() == 'P'.code && stream.read() == 'K'.code) { "Downloaded file is not a valid APK" }
            }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Could not finalize the downloaded APK" }
            validateDownloadedApk(context, target).getOrThrow()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(KEY_DOWNLOADED_BASE_VERSION, installedVersionCode(context))
                .apply()
            onProgress(1f, total, total)
            target
        }.onSuccess {
            runCatching { notificationManager.cancel(notificationId) }
        }.onFailure {
            runCatching { notificationManager.cancel(notificationId) }
        }.onFailure {
            partial.delete()
            target.delete()
        }
    }

    suspend fun download(
        context: Context,
        update: GitHubReleaseUpdate,
        onProgress: (Float) -> Unit,
    ): Result<File> = download(context, update) { progress, _, _ -> onProgress(progress) }

    fun validateDownloadedApk(context: Context, apk: File): Result<Unit> = runCatching {
        check(apk.isFile && apk.length() > 4L && apk.extension.equals("apk", ignoreCase = true)) {
            "Downloaded update is not a valid APK file"
        }
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Android could not read the downloaded update")
        check(archive.packageName == context.packageName) { "Update package does not match VYBE" }
        val archiveCode = PackageInfoCompat.getLongVersionCode(archive)
        val installedCode = installedVersionCode(context)
        val archiveName = archive.versionName.orEmpty()
        val installedName = installedVersionName(context).orEmpty()
        val isNewer = archiveCode > installedCode || (archiveCode >= installedCode && (isNewerVersion(archiveName, installedName) || archiveName != installedName))
        check(isNewer) {
            "Downloaded update is not newer than the installed VYBE version"
        }
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val installedSigners = signerDigests(installed)
        val archiveSigners = signerDigests(archive)
        check(installedSigners.isNotEmpty() && installedSigners == archiveSigners) {
            "Update signature does not match the installed VYBE app"
        }
    }

    /** Cleans completed/obsolete installer files without touching offline music. */
    fun cleanupTemporaryUpdates(context: Context) {
        val directory = File(context.cacheDir, "app_updates")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val baseVersion = prefs.getLong(KEY_DOWNLOADED_BASE_VERSION, -1L)
        val updateCompleted = baseVersion >= 0L && installedVersionCode(context) > baseVersion
        val expiry = System.currentTimeMillis() - UPDATE_FILE_MAX_AGE_MS
        directory.listFiles()?.forEach { file ->
            if (file.name.endsWith(".partial") || updateCompleted || file.lastModified() < expiry) {
                file.delete()
            }
        }
        if (updateCompleted) prefs.edit().remove(KEY_DOWNLOADED_BASE_VERSION).apply()
    }

    fun dismiss(context: Context, update: GitHubReleaseUpdate) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_TAG, versionKey(update.tagName))
            .remove(KEY_REMIND_AFTER)
            .apply()
        // A periodic worker may have posted this notification just before Skip was tapped.
        // Remove it immediately; future worker checks remain suppressed for this exact version.
        NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID)
    }

    fun remindLater(context: Context, delayMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_REMIND_AFTER, System.currentTimeMillis() + delayMillis.coerceAtLeast(0L))
            .apply()
    }

    fun clearDeferral(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_REMIND_AFTER)
            .apply()
    }

    fun launchInstaller(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return true
    }

    private fun apkScore(name: String): Int {
        val lower = name.lowercase()
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase()
        return when {
            "debug" in lower -> -100
            abi.isNotBlank() && abi in lower -> 100
            "arm64" in abi && "arm64" in lower -> 95
            "universal" in lower -> 80
            "release" in lower -> 60
            else -> 10
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val left = versionParts(remote)
        val right = versionParts(current)
        val length = maxOf(left.size, right.size)
        for (index in 0 until length) {
            val comparison = (left.getOrElse(index) { 0 }).compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    private fun versionParts(value: String): List<Int> {
        // Accept GitHub tags such as v0.10.0, VYBE-0.10.0 and plain Android names.
        val semantic = Regex("""\d+(?:\.\d+)+""").find(value)?.value ?: return emptyList()
        return semantic.split('.').mapNotNull(String::toIntOrNull)
    }

    private fun versionKey(value: String): String = versionParts(value).joinToString(".")

    private fun releaseVersionCode(notes: String): Long? =
        Regex("""(?im)^\s*(?:android\s+)?version\s*code\s*:\s*(\d+)\s*$""")
            .find(notes)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private data class ManifestLookup(
        val available: Boolean,
        val update: GitHubReleaseUpdate?,
    )

    private fun manifestUpdate(
        context: Context,
        owner: String,
        repo: String,
        respectDismissal: Boolean,
    ): ManifestLookup {
        val url = "https://raw.githubusercontent.com/$owner/$repo/main/update-manifest.json?t=${System.currentTimeMillis()}"
        val response = openConnection(url, accept = "application/json").apply {
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache, no-store")
            setRequestProperty("Pragma", "no-cache")
        }.useResponse()
        if (response.code == HttpURLConnection.HTTP_NOT_FOUND) return ManifestLookup(false, null)
        check(response.code in 200..299) { "VYBE update manifest failed (${response.code})" }

        val manifest = JSONObject(response.body)
        check(manifest.optInt("schemaVersion", 0) == 1) { "Unsupported VYBE update manifest" }
        check(manifest.optString("packageName") == context.packageName) {
            "Update manifest package does not match VYBE"
        }
        val remoteCode = manifest.optLong("versionCode", -1L)
        check(remoteCode > 0L) { "Update manifest has no valid Android version code" }
        // Numeric Android versionCode is the sole upgrade authority.
        if (remoteCode <= installedVersionCode(context)) return ManifestLookup(true, null)

        val tag = manifest.optString("tag").trim()
        check(tag.isNotBlank()) { "Update manifest has no release tag" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (respectDismissal && versionKey(prefs.getString(KEY_DISMISSED_TAG, null).orEmpty()) == versionKey(tag)) {
            return ManifestLookup(true, null)
        }
        if (respectDismissal && System.currentTimeMillis() < prefs.getLong(KEY_REMIND_AFTER, 0L)) {
            return ManifestLookup(true, null)
        }

        val assets = manifest.optJSONArray("assets") ?: error("Update manifest has no APK assets")
        val candidates = buildList {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val downloadUrl = asset.optString("url")
                if (name.endsWith(".apk", true) && downloadUrl.startsWith("https://")) {
                    add(asset)
                }
            }
        }
        val selected = candidates.maxByOrNull { apkScore(it.optString("name")) }
            ?: error("Update manifest has no compatible APK")
        val declaredSize = selected.optLong("size", 0L).takeIf { it > 0L }
            ?: selected.optLong("sizeBytes", 0L).takeIf { it > 0L }
            ?: selected.optLong("apkSizeBytes", 0L).takeIf { it > 0L }
            ?: 0L
        val apkUrl = selected.optString("url")
        val apkSizeBytes = if (declaredSize > 0L) declaredSize else resolveRemoteFileSize(apkUrl)
        return ManifestLookup(
            available = true,
            update = GitHubReleaseUpdate(
                tagName = tag,
                title = manifest.optString("title").ifBlank { "VYBE $tag" },
                notes = manifest.optString("notes").trim().take(MAX_NOTES_LENGTH),
                versionCode = remoteCode,
                apkName = selected.optString("name"),
                apkUrl = apkUrl,
                apkSizeBytes = apkSizeBytes,
                apkSha256 = selected.optString("sha256").trim().ifBlank { null },
            ),
        )
    }

    private fun resolveRemoteFileSize(url: String): Long {
        if (url.isBlank()) return 0L
        return runCatching {
            var currentUrl = url
            var redirects = 0
            while (redirects < 6) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    requestMethod = "HEAD"
                    setRequestProperty("User-Agent", "VYBE/${BuildConfig.VERSION_NAME}")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank()) {
                        currentUrl = location
                        redirects++
                        continue
                    }
                }
                val length = conn.contentLengthLong
                conn.disconnect()
                if (length > 0L) return length
                break
            }
            0L
        }.getOrDefault(0L)
    }

    private fun openDownloadConnection(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "VYBE/${BuildConfig.VERSION_NAME}")
            }
            val status = conn.responseCode
            if (status in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (!location.isNullOrBlank()) {
                    currentUrl = location
                    redirects++
                    continue
                }
            }
            return conn
        }
        return (URL(currentUrl).openConnection() as HttpURLConnection)
    }

    private fun openConnection(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "VYBE/${BuildConfig.VERSION_NAME}")
        }

    private data class HttpResponse(val code: Int, val body: String)

    private fun HttpURLConnection.useResponse(): HttpResponse = try {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        HttpResponse(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    } finally {
        disconnect()
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .takeIf { it.endsWith(".apk", ignoreCase = true) }
        ?: "VYBE-update.apk"

    private fun fileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun installedVersionName(context: Context): String? {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName
    }

    private fun installedVersionCode(context: Context): Long {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else info.signatures.orEmpty()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }

    companion object {
        const val PREFS_NAME = "vybe_app_updates"
        const val KEY_DISMISSED_TAG = "dismissed_release_tag"
        const val KEY_REMIND_AFTER = "remind_after_timestamp"
        const val KEY_DOWNLOADED_BASE_VERSION = "downloaded_base_version"
        const val MAX_NOTES_LENGTH = 1_200
        const val UPDATE_FILE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
        const val UPDATE_NOTIFICATION_ID = 7012
    }
}
