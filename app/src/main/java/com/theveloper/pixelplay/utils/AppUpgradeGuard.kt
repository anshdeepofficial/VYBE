package com.theveloper.pixelplay.utils

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/** Keeps durable user data while removing transient files that are unsafe across app upgrades. */
object AppUpgradeGuard {
    private const val PREFS = "vybe_upgrade_guard"
    private const val LAST_VERSION = "last_started_version_code"

    fun run(context: Context) {
        val appContext = context.applicationContext
        val currentVersion = runCatching {
            @Suppress("DEPRECATION")
            PackageInfoCompat.getLongVersionCode(
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            )
        }.getOrDefault(0L)
        if (currentVersion <= 0L) return

        val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousVersion = preferences.getLong(LAST_VERSION, 0L)
        if (previousVersion in 1 until currentVersion) {
            listOf(
                File(appContext.cacheDir, "app_updates"),
                File(appContext.cacheDir, "external_audio"),
                File(appContext.cacheDir, "external_artwork"),
                File(appContext.cacheDir, "shared_zips"),
            ).forEach(::deleteSafely)
            // A report from the old binary is not actionable after the upgrade succeeds.
            appContext.getSharedPreferences("crash_handler_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
        preferences.edit().putLong(LAST_VERSION, currentVersion).apply()
    }

    private fun deleteSafely(target: File) {
        val cacheRoot = target.parentFile?.canonicalFile ?: return
        val resolved = runCatching { target.canonicalFile }.getOrNull() ?: return
        if (!resolved.path.startsWith(cacheRoot.path + File.separator)) return
        resolved.deleteRecursively()
    }
}
