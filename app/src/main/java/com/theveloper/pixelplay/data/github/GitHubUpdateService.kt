package com.theveloper.pixelplay.data.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.theveloper.pixelplay.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class GitHubReleaseUpdate(
    val tagName: String,
    val title: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
)

class GitHubUpdateService {

    suspend fun checkForUpdate(context: Context): Result<GitHubReleaseUpdate?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val owner = BuildConfig.VYBE_GITHUB_OWNER.trim()
                val repo = BuildConfig.VYBE_GITHUB_REPO.trim()
                if (owner.isBlank() || repo.isBlank()) return@runCatching null

                val connection = openConnection(
                    "https://api.github.com/repos/$owner/$repo/releases/latest",
                    accept = "application/vnd.github+json",
                )
                val response = connection.useResponse()
                if (response.code == HttpURLConnection.HTTP_NOT_FOUND) return@runCatching null
                check(response.code in 200..299) { "GitHub update check failed (${response.code})" }

                val release = JSONObject(response.body)
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@runCatching null
                val tag = release.optString("tag_name").trim()
                if (tag.isBlank() || !isNewerVersion(tag, BuildConfig.VERSION_NAME)) return@runCatching null

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
                val dismissedTag = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_DISMISSED_TAG, null)
                if (dismissedTag == tag) return@runCatching null

                GitHubReleaseUpdate(
                    tagName = tag,
                    title = release.optString("name").ifBlank { "VYBE $tag" },
                    notes = release.optString("body").trim().take(MAX_NOTES_LENGTH),
                    apkName = selected.first,
                    apkUrl = selected.second,
                    apkSizeBytes = selected.third,
                )
            }
        }

    suspend fun download(
        context: Context,
        update: GitHubReleaseUpdate,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, "app_updates").apply { mkdirs() }
            val target = File(directory, safeFileName(update.apkName))
            val partial = File(directory, "${target.name}.partial")
            if (partial.exists()) partial.delete()

            val connection = openConnection(update.apkUrl, accept = "application/octet-stream")
            val code = connection.responseCode
            check(code in 200..299) { "Update download failed ($code)" }
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: update.apkSizeBytes
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0L) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            connection.disconnect()
            check(partial.length() > 4L) { "Downloaded APK is empty" }
            partial.inputStream().use { stream ->
                check(stream.read() == 'P'.code && stream.read() == 'K'.code) { "Downloaded file is not a valid APK" }
            }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Could not finalize the downloaded APK" }
            onProgress(1f)
            target
        }
    }

    fun dismiss(context: Context, update: GitHubReleaseUpdate) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_TAG, update.tagName)
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

    private fun versionParts(value: String): List<Int> = value
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

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

    private companion object {
        const val PREFS_NAME = "vybe_app_updates"
        const val KEY_DISMISSED_TAG = "dismissed_release_tag"
        const val MAX_NOTES_LENGTH = 1_200
    }
}
