package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeAccountManager
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeSyncState
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeSettingsSyncManager
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeSettingsSyncState
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExternalServiceAccount {
    TELEGRAM,
    GOOGLE_DRIVE,
    YOUTUBE_MUSIC,
    NETEASE,
    QQ_MUSIC,
    NAVIDROME,
    JELLYFIN,
    SPOTIFY
}

data class ExternalAccountUiModel(
    val service: ExternalServiceAccount,
    val title: String,
    val accountLabel: String,
    val syncedContentLabel: String,
    val isLoggingOut: Boolean,
    val libraryCount: Int = 0,
    val likedCount: Int = 0,
    val playlistCount: Int = 0,
    val historyCount: Int = 0,
)

data class AccountsUiState(
    val connectedAccounts: List<ExternalAccountUiModel> = emptyList(),
    val disconnectedServices: List<ExternalServiceAccount> = emptyList()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val youTubeAccountManager: YouTubeAccountManager,
    private val youTubeSettingsSyncManager: YouTubeSettingsSyncManager,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val youTubeSyncState: StateFlow<YouTubeSyncState> = youTubeAccountManager.syncStateFlow
    val isYouTubeLoggedIn: StateFlow<Boolean> = youTubeAccountManager.isLoggedInFlow
    val youTubeAccountName: StateFlow<String> = youTubeAccountManager.accountNameFlow
    val youTubeAccountAvatarUrl: StateFlow<String?> = youTubeAccountManager.accountAvatarUrlFlow
    val youTubeSettingsSyncState: StateFlow<YouTubeSettingsSyncState> = youTubeSettingsSyncManager.state
    val showFirstYouTubeMusicSignInPrompt: StateFlow<Boolean> = combine(
        userPreferencesRepository.initialSetupDoneFlow,
        userPreferencesRepository.youtubeMusicSignInPromptShownFlow,
        youTubeAccountManager.isLoggedInFlow,
    ) { setupDone, promptShown, loggedIn -> setupDone && !promptShown && !loggedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun restoreYouTubeMusicSettings() = youTubeSettingsSyncManager.restore()
    fun backupCurrentYouTubeMusicSettings() = youTubeSettingsSyncManager.backupCurrentDevice()
    fun skipYouTubeMusicSettingsRestore() = youTubeSettingsSyncManager.skipRestore()

    fun markFirstYouTubeMusicSignInPromptShown() {
        viewModelScope.launch { userPreferencesRepository.setYouTubeMusicSignInPromptShown(true) }
    }

    private val loggingOutServices = MutableStateFlow<Set<ExternalServiceAccount>>(emptySet())

    val uiState: StateFlow<AccountsUiState> = combine(
        youTubeAccountManager.isLoggedInFlow,
        youTubeAccountManager.syncedCountFlow,
        youTubeAccountManager.libraryStatsFlow,
        loggingOutServices
    ) { youTubeConnected, youTubeSyncedCount, stats, activeLogouts ->
        val connectedAccounts = buildList {
            if (youTubeConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.YOUTUBE_MUSIC,
                        title = "YouTube Music",
                        accountLabel = youTubeAccountManager.accountNameFlow.value,
                        syncedContentLabel = formatCount(
                            count = youTubeSyncedCount,
                            singular = "synced track",
                            plural = "synced tracks"
                        ),
                        isLoggingOut = ExternalServiceAccount.YOUTUBE_MUSIC in activeLogouts,
                        libraryCount = stats.library,
                        likedCount = stats.liked,
                        playlistCount = stats.playlists,
                        historyCount = stats.history,
                    )
                )
            }
        }

        val disconnectedServices = buildList {
            if (!youTubeConnected) add(ExternalServiceAccount.YOUTUBE_MUSIC)
        }

        AccountsUiState(
            connectedAccounts = connectedAccounts,
            disconnectedServices = disconnectedServices
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun connectYouTubeMusic(cookie: String) {
        youTubeAccountManager.loginWithAuth(cookie)
    }

    fun syncYouTubeMusic() {
        youTubeAccountManager.syncLibrary()
    }

    fun logout(service: ExternalServiceAccount) {
        if (service in loggingOutServices.value) return

        viewModelScope.launch {
            loggingOutServices.update { it + service }
            try {
                runCatching {
                    if (service == ExternalServiceAccount.YOUTUBE_MUSIC) {
                        youTubeAccountManager.logout()
                    }
                }
            } finally {
                loggingOutServices.update { it - service }
            }
        }
    }

    private fun formatCount(count: Int, singular: String, plural: String): String {
        return if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }
}
