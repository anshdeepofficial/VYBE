package com.theveloper.pixelplay.presentation.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.spotify.auth.SpotifyLoginActivity
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@Composable
fun SpotifySettingsSection(settingsViewModel: SettingsViewModel, context: Context) {
    val loggedIn by settingsViewModel.spotifyIsLoggedIn.collectAsStateWithLifecycle()
    val accountName by settingsViewModel.spotifyAccountName.collectAsStateWithLifecycle()
    val library by settingsViewModel.spotifyLibraryState.collectAsStateWithLifecycle()
    val publicImport by settingsViewModel.spotifyImportState.collectAsStateWithLifecycle()
    var playlistUrl by remember { mutableStateOf("") }

    LaunchedEffect(loggedIn) {
        if (loggedIn) settingsViewModel.loadSpotifyAccountPlaylists()
    }

    SettingsSubsection(title = "Spotify account") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!loggedIn) {
                Text(
                    "Connect Spotify to sync private, collaborative, followed, and public playlists without Developer Mode limits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { context.startActivity(Intent(context, SpotifyLoginActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.AccountCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in with Spotify")
                }

            } else {
                Text("Connected as ${accountName.ifBlank { "Spotify user" }}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = settingsViewModel::loadSpotifyAccountPlaylists,
                        enabled = !library.isLoading && !library.isImporting,
                        modifier = Modifier.weight(1f),
                    ) { Text("Refresh") }
                    TextButton(onClick = settingsViewModel::logoutSpotify, modifier = Modifier.weight(1f)) { Text("Log out") }
                }
                if (library.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                library.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                
                if (library.playlists.isNotEmpty()) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(library.playlists, key = { it.id }) { playlist ->
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = playlist.id in library.selectedPlaylistIds,
                                            onCheckedChange = { settingsViewModel.toggleSpotifyPlaylistSelection(playlist.id) },
                                            enabled = !library.isImporting,
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${playlist.trackCount} songs - ${playlist.ownerName}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (playlist.id in library.selectedPlaylistIds) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Also sync to YouTube Music", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                            Switch(
                                                checked = playlist.id in library.youtubeSyncPlaylistIds,
                                                onCheckedChange = { settingsViewModel.setSpotifyPlaylistYouTubeSync(playlist.id, it) },
                                                enabled = !library.isImporting,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = settingsViewModel::importSelectedSpotifyPlaylists,
                        enabled = library.selectedPlaylistIds.isNotEmpty() && !library.isImporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (library.isImporting) "Importing ${library.processedPlaylists}/${library.totalPlaylists}..." else "Import selected playlists") }
                } else if (!library.isLoading) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "⚡ Direct Playlist Import",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                            Text(
                                "Due to Spotify API Development Mode limits, private library listing is restricted. You can import any playlist instantly by copying its link in Spotify and pasting it in the box below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (library.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())
                library.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    SettingsSubsection(title = "Public playlist URL") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Public-link import works for any Spotify playlist link without account restrictions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = playlistUrl,
                onValueChange = { playlistUrl = it },
                label = { Text("Spotify playlist URL") },
                trailingIcon = {
                    TextButton(onClick = {
                        clipboardManager.getText()?.text?.let { text ->
                            if (text.isNotBlank()) playlistUrl = text.trim()
                        }
                    }) {
                        Text("Paste")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { settingsViewModel.importSpotifyPlaylist(playlistUrl) },
                enabled = playlistUrl.isNotBlank() && !publicImport.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(if (publicImport.isImporting) "Importing..." else "Import public playlist")
            }
            if (publicImport.isImporting) {
                LinearProgressIndicator(progress = { publicImport.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${publicImport.processed} of ${publicImport.total} songs")
            }
            publicImport.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            publicImport.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
