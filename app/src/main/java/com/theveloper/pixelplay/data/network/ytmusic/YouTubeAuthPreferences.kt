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
        val secure = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Throwable) {
            timber.log.Timber.e(e, "YouTubeAuthPreferences: Keystore failed. Falling back to plain SharedPreferences.")
            context.getSharedPreferences(SECURE_PREFS_NAME + "_plain", Context.MODE_PRIVATE)
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
