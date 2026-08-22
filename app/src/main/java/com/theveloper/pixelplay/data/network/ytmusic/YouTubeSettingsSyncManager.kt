package com.theveloper.pixelplay.data.network.ytmusic

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.preferences.PreferenceBackupEntry
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

sealed interface YouTubeSettingsSyncState {
    data object Idle : YouTubeSettingsSyncState
    data object Checking : YouTubeSettingsSyncState
    data class RestoreAvailable(val settingCount: Int) : YouTubeSettingsSyncState
    data object Restoring : YouTubeSettingsSyncState
    data object Restored : YouTubeSettingsSyncState
    data class Error(val message: String) : YouTubeSettingsSyncState
}

private data class SettingsBackupEnvelope(
    val version: Int = 1,
    val updatedAt: Long,
    val entries: List<PreferenceBackupEntry>,
)

/**
 * Stores an encrypted, compressed settings snapshot in private YouTube Music playlist metadata.
 * The account identity derives the encryption key, so API keys are never uploaded as plain text.
 */
@Singleton
class YouTubeSettingsSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val accountManager: YouTubeAccountManager,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val devicePreferences = context.getSharedPreferences(DEVICE_PREFS_NAME, Context.MODE_PRIVATE)
    private val checkedAccounts = mutableSetOf<String>()
    private val _state = MutableStateFlow<YouTubeSettingsSyncState>(YouTubeSettingsSyncState.Idle)
    val state: StateFlow<YouTubeSettingsSyncState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(
                accountManager.isLoggedInFlow,
                accountManager.accountIdentityFlow,
            ) { loggedIn, identity -> loggedIn to identity }
                .distinctUntilChanged()
                .collect { (loggedIn, identity) ->
                    if (!loggedIn || identity.isBlank()) {
                        _state.value = YouTubeSettingsSyncState.Idle
                    } else if (checkedAccounts.add(identity)) {
                        checkRemoteBackup(identity)
                    }
                }
        }
        scope.launch {
            @Suppress("OPT_IN_USAGE")
            userPreferencesRepository.backupRevisionFlow()
                .debounce(2_000)
                .collect {
                    val identity = accountManager.accountIdentityFlow.value
                    if (
                        accountManager.isLoggedInFlow.value &&
                        identity.isNotBlank() &&
                        isHandled(identity) &&
                        !isOptedOut(identity) &&
                        _state.value !is YouTubeSettingsSyncState.Restoring
                    ) {
                        runCatching { uploadCurrentSettings(identity) }
                            .onFailure { error ->
                                _state.value = YouTubeSettingsSyncState.Error(
                                    error.message ?: "Settings backup upload failed"
                                )
                            }
                    }
                }
        }
    }

    fun restore() {
        val identity = accountManager.accountIdentityFlow.value
        if (identity.isBlank() || _state.value !is YouTubeSettingsSyncState.RestoreAvailable) return
        scope.launch {
            _state.value = YouTubeSettingsSyncState.Restoring
            runCatching {
                val (_, payload) = accountManager.loadRemoteSettingsBackup()
                val entries = decode(payload ?: error("The settings backup is no longer available."), identity).entries
                userPreferencesRepository.importPreferencesFromBackup(entries, clearExisting = true)
                markHandled(identity, optedOut = false)
            }.onSuccess {
                _state.value = YouTubeSettingsSyncState.Restored
                delay(2_000)
                _state.value = YouTubeSettingsSyncState.Idle
            }.onFailure { error ->
                _state.value = YouTubeSettingsSyncState.Error(error.message ?: "Settings restore failed")
            }
        }
    }

    fun skipRestore() {
        val identity = accountManager.accountIdentityFlow.value
        if (identity.isBlank()) return
        markHandled(identity, optedOut = true)
        _state.value = YouTubeSettingsSyncState.Idle
    }

    private suspend fun checkRemoteBackup(identity: String) {
        if (isHandled(identity)) {
            _state.value = YouTubeSettingsSyncState.Idle
            return
        }
        _state.value = YouTubeSettingsSyncState.Checking
        runCatching { accountManager.loadRemoteSettingsBackup().second }
            .onSuccess { payload ->
                if (payload.isNullOrBlank()) {
                    runCatching { uploadCurrentSettings(identity) }
                        .onSuccess {
                            markHandled(identity, optedOut = false)
                            _state.value = YouTubeSettingsSyncState.Idle
                        }
                        .onFailure { error ->
                            _state.value = YouTubeSettingsSyncState.Error(
                                error.message ?: "Settings backup upload failed"
                            )
                        }
                } else {
                    runCatching { decode(payload, identity) }
                        .onSuccess { _state.value = YouTubeSettingsSyncState.RestoreAvailable(it.entries.size) }
                        .onFailure { _state.value = YouTubeSettingsSyncState.Error("The remote settings backup could not be read securely.") }
                }
            }
            .onFailure { error ->
                _state.value = YouTubeSettingsSyncState.Error(error.message ?: "Could not check settings backup")
            }
    }

    private suspend fun uploadCurrentSettings(identity: String) {
        val entries = userPreferencesRepository.exportPreferencesForBackup()
        val envelope = SettingsBackupEnvelope(updatedAt = System.currentTimeMillis(), entries = entries)
        accountManager.saveRemoteSettingsBackup(encode(envelope, identity))
    }

    private fun encode(envelope: SettingsBackupEnvelope, identity: String): String {
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(gson.toJson(envelope).toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(identity), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(compressed)
        return "v1.${Base64.encodeToString(iv + encrypted, Base64.NO_WRAP or Base64.URL_SAFE)}"
    }

    private fun decode(payload: String, identity: String): SettingsBackupEnvelope {
        require(payload.startsWith("v1.")) { "Unsupported settings backup version" }
        val bytes = Base64.decode(payload.removePrefix("v1."), Base64.NO_WRAP or Base64.URL_SAFE)
        require(bytes.size > 28) { "Invalid settings backup" }
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(identity), GCMParameterSpec(128, iv))
        val json = GZIPInputStream(ByteArrayInputStream(cipher.doFinal(encrypted)))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val type = object : TypeToken<SettingsBackupEnvelope>() {}.type
        return gson.fromJson(json, type)
    }

    private fun key(identity: String) = SecretKeySpec(
        MessageDigest.getInstance("SHA-256")
            .digest("PixelPlayer.YouTubeMusic.Settings.v1|$identity".toByteArray()),
        "AES",
    )

    private fun isHandled(identity: String) = devicePreferences.getBoolean("handled_$identity", false)
    private fun isOptedOut(identity: String) = devicePreferences.getBoolean("opt_out_$identity", false)
    private fun markHandled(identity: String, optedOut: Boolean) {
        devicePreferences.edit()
            .putBoolean("handled_$identity", true)
            .putBoolean("opt_out_$identity", optedOut)
            .apply()
    }

    private companion object {
        const val DEVICE_PREFS_NAME = "youtube_music_settings_sync_device"
    }
}
