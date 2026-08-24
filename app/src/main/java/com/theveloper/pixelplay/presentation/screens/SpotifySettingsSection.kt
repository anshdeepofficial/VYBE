package com.theveloper.pixelplay.presentation.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.spotify.auth.SpotifyLoginActivity
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@Composable
fun SpotifySettingsSection(settingsViewModel: SettingsViewModel, context: Context) {
    val isLoggedIn by settingsViewModel.spotifyIsLoggedIn.collectAsStateWithLifecycle()
    val accountName by settingsViewModel.spotifyAccountName.collectAsStateWithLifecycle()
    val libraryState by settingsViewModel.spotifyLibraryState.collectAsStateWithLifecycle()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            settingsViewModel.loadSpotifyAccountPlaylists()
        }
    }

    SettingsSubsection(title = "Spotify Account") {
        if (isLoggedIn) {
            SettingsItem(
                title = accountName.ifBlank { "Spotify User" },
                subtitle = "Connected",
                leadingIcon = { Icon(Icons.Rounded.AccountCircle, null) },
                onClick = {}
            )
            val playlistCount = libraryState.playlists.size
            SettingsItem(
                title = "Playlists",
                subtitle = if (libraryState.isLoading) "Loading..." else "$playlistCount playlists available",
                leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) },
                onClick = {}
            )
            SettingsItem(
                title = "Import Playlists",
                subtitle = "Import your Spotify playlists into VYBE",
                leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { settingsViewModel.importSelectedSpotifyPlaylists() }
            )
            SettingsItem(
                title = "Disconnect",
                subtitle = "Sign out of your Spotify account",
                leadingIcon = { Icon(Icons.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    settingsViewModel.logoutSpotify()
                    // Since SettingsViewModel doesn't expose it directly, 
                    // we'll need to add a function or use the intent. Wait, actually we can just start the activity or clear prefs.
                }
            )
        } else {
            SettingsItem(
                title = "Connect to Spotify",
                subtitle = "Sign in to import your playlists and profile",
                leadingIcon = { Icon(Icons.Rounded.Login, null) },
                onClick = {
                    context.startActivity(Intent(context, SpotifyLoginActivity::class.java))
                }
            )
        }
    }
}
