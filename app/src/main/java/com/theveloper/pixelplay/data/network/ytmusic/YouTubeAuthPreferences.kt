package com.theveloper.pixelplay.data.network.ytmusic

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Creates the encrypted preference store used for YouTube Music sessions.
 * Plaintext values from older builds are migrated once and then removed.
 */
object YouTubeAuthPreferences {
    const val SECURE_PREFS_NAME = "ytmusic_auth_secure_prefs"
    private const val LEGACY_PREFS_NAME = "ytmusic_auth_prefs"

    fun create(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val secure = try {
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            timber.log.Timber.e(e, "YouTubeAuthPreferences: Failed to create EncryptedSharedPreferences. Clearing corrupted file.")
            val dir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
            val file = java.io.File(dir, "$SECURE_PREFS_NAME.xml")
            if (file.exists()) file.delete()
            
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        migrateLegacyPreferences(context, secure)
        return secure
    }

    private fun migrateLegacyPreferences(context: Context, secure: SharedPreferences) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return

        val editor = secure.edit()
        legacy.all.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        if (editor.commit()) legacy.edit().clear().commit()
    }
}
