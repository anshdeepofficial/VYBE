package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.recognition.SongRecognitionLauncher
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SongRecognitionViewModel

@Composable
fun SongRecognitionScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    viewModel: SongRecognitionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.resolve(SongRecognitionLauncher.results(result.data))
    }
    Column(
        Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Mic, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp))
        Text("Recognize a song", style = MaterialTheme.typography.headlineMedium)
        Text("Say the song title or artist. VYBE will find and play it inside the app.")
        Spacer(Modifier.height(18.dp))
        Button(onClick = { launcher.launch(SongRecognitionLauncher.createIntent()) }, enabled = !state.isSearching) {
            Text(if (state.isSearching) "Searching VYBE…" else "Listen")
        }
        if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp)) }
        state.matches.firstOrNull()?.let { first ->
            Spacer(Modifier.height(16.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable {
                    playerViewModel.playSongs(state.matches, first, "Recognized: ${state.query}")
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(first.title, style = MaterialTheme.typography.titleMedium)
                    Text(first.displayArtist)
                    Text("Tap to play in VYBE", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
