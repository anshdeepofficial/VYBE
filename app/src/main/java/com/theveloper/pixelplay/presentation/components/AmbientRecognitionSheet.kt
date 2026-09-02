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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.cancel()
                        onDismiss()
                    }
                ) {
                    Text("Cancel")
                }
            } else {
                state.result?.let { match ->
                    val offset = match.metadata.matchedOffsetSeconds
                    val offsetText = if (offset != null && offset > 0f) {
                        val minutes = (offset / 60).toInt()
                        val seconds = (offset % 60).toInt()
                        "%d:%02d".format(minutes, seconds)
                    } else null

                    AsyncImage(
                        model = match.metadata.artworkUrl ?: match.song?.albumArtUriString,
                        contentDescription = null,
                        modifier = Modifier.size(150.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(match.metadata.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(match.metadata.artist, textAlign = TextAlign.Center)
                    match.metadata.album?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
                    
                    if (offsetText != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Matched at $offsetText",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    match.song?.let { song ->
                        if (offsetText != null && offset != null && offset > 0f) {
                            val offsetMs = (offset * 1000L).toLong()
                            Button(
                                onClick = {
                                    viewModel.cancel()
                                    playerViewModel.playSongs(listOf(song), song, "Recognized song ($offsetText)", startPositionMs = offsetMs)
                                    onDismiss()
                                    playerViewModel.showPlayer()
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                            ) { Text("Play from $offsetText") }
                            Spacer(Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.cancel()
                                    playerViewModel.playSongs(listOf(song), song, "Recognized song", startPositionMs = 0L)
                                    onDismiss()
                                    playerViewModel.showPlayer()
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                            ) { Text("Play from beginning (0:00)") }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.cancel()
                                    playerViewModel.playSongs(listOf(song), song, "Recognized song", startPositionMs = 0L)
                                    onDismiss()
                                    playerViewModel.showPlayer()
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                            ) { Text("Play in VYBE") }
                        }
                    } ?: Text("Matched, but no playable track was found.", color = MaterialTheme.colorScheme.error)
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = start,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (state.phase == AmbientRecognitionPhase.READY) "Listen" else "Listen Again")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.cancel()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Skip")
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
