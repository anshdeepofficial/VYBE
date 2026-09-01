package com.theveloper.pixelplay.presentation.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.theveloper.pixelplay.presentation.viewmodel.AmbientRecognitionPhase
import com.theveloper.pixelplay.presentation.viewmodel.AmbientRecognitionViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientRecognitionSheet(
    onDismiss: () -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: AmbientRecognitionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.start() else viewModel.permissionDenied()
    }
    val start = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.start()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    DisposableEffect(Unit) { onDispose { viewModel.cancel() } }

    ModalBottomSheet(onDismissRequest = { viewModel.cancel(); onDismiss() }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val listening = state.phase == AmbientRecognitionPhase.LISTENING || state.phase == AmbientRecognitionPhase.PROCESSING
            val transition = rememberInfiniteTransition(label = "recognitionPulse")
            val scale by transition.animateFloat(
                initialValue = 0.9f,
                targetValue = if (listening) 1.15f else 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "recognitionScale",
            )
            Icon(Icons.Rounded.GraphicEq, null, Modifier.size(76.dp).scale(scale), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Recognize a song", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                when (state.phase) {
                    AmbientRecognitionPhase.LISTENING -> "Listening… ${state.elapsedSeconds}s / 14s"
                    AmbientRecognitionPhase.PROCESSING -> "Finding the song…"
                    else -> "Play a clear part of the song near your phone."
                },
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            if (listening) {
                CircularProgressIndicator()
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = viewModel::cancel) { Text("Cancel") }
            } else {
                state.result?.let { match ->
                    AsyncImage(
                        model = match.metadata.artworkUrl ?: match.song?.albumArtUriString,
                        contentDescription = null,
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Text(match.metadata.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(match.metadata.artist)
                    match.metadata.album?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(12.dp))
                    match.song?.let { song ->
                        Button(onClick = { playerViewModel.playSongs(listOf(song), song, "Recognized song") }) { Text("Play in VYBE") }
                    } ?: Text("Matched, but no playable VYBE track was found.", color = MaterialTheme.colorScheme.error)
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = start) { Text(if (state.phase == AmbientRecognitionPhase.READY) "Listen" else "Try Again") }
                    if (state.phase != AmbientRecognitionPhase.READY) OutlinedButton(onClick = { viewModel.cancel(); onDismiss() }) { Text("Close") }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
