package com.theveloper.pixelplay.presentation.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@Composable
fun SpotifySettingsSection(settingsViewModel: SettingsViewModel, context: Context) {
    var playlistUrl by remember { mutableStateOf("") }

    SettingsSubsection(title = "Spotify Playlist Importer") {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Import your public Spotify playlists directly without needing an account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = playlistUrl,
                onValueChange = { playlistUrl = it },
                label = { Text("Spotify Playlist URL") },
                placeholder = { Text("https://open.spotify.com/playlist/...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { 
                    if (playlistUrl.isNotBlank()) {
                        Toast.makeText(context, "Playlist import will be available in the next update.", Toast.LENGTH_SHORT).show()
                        playlistUrl = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Playlist")
            }
        }
    }
}
