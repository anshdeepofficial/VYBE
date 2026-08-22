package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@Composable
fun SpotifyMusicManagementSection(settingsViewModel: SettingsViewModel) {
    val importState by settingsViewModel.spotifyImportState.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var publicLink by remember { mutableStateOf("") }
    var publicSync by remember { mutableStateOf(false) }

    Text(
        text = "Spotify Music Management",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
    )
    SettingsItem(
        title = "Import Spotify playlist",
        subtitle = "Import any public Spotify playlist link — no Spotify login required",
        leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.secondary) },
        onClick = {
            settingsViewModel.clearSpotifyImportState()
            showDialog = true
        },
    )

    if (!showDialog) return

    AlertDialog(
        onDismissRequest = {
            if (!importState.isImporting) showDialog = false
        },
        title = { Text("Import Spotify playlist", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Paste a public Spotify playlist URL. VYBE will copy it once and match its songs to playable sources.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = publicLink,
                    onValueChange = { publicLink = it },
                    label = { Text("Spotify playlist URL") },
                    singleLine = true,
                    enabled = !importState.isImporting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sync to YouTube Music", modifier = Modifier.weight(1f))
                    Switch(
                        checked = publicSync,
                        onCheckedChange = { publicSync = it },
                        enabled = !importState.isImporting,
                    )
                }
                Button(
                    onClick = { settingsViewModel.importSpotifyPlaylist(publicLink, publicSync) },
                    enabled = publicLink.isNotBlank() && !importState.isImporting,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (importState.isImporting) "Importing…" else "Import public playlist") }
                if (importState.isImporting) {
                    Text(
                        text = if (importState.total > 0) {
                            "Matching ${importState.processed} of ${importState.total} songs…"
                        } else {
                            "Reading all songs from the public playlist…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (importState.total > 0) {
                        LinearProgressIndicator(
                            progress = { importState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                importState.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                importState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { showDialog = false },
                enabled = !importState.isImporting,
            ) { Text("Done") }
        },
    )
}
