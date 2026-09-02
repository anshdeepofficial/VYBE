package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.theveloper.pixelplay.data.recognition.ReelRecognitionResult
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelRecognitionBottomSheet(
    result: ReelRecognitionResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    playerViewModel: PlayerViewModel,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isLoading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Analyzing Reel & identifying song…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Matching audio with official music…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            } else if (result != null) {
                val song = result.song
                val offset = result.matchedOffsetSeconds
                val offsetText = if (offset != null && offset > 0f) {
                    val minutes = (offset / 60).toInt()
                    val seconds = (offset % 60).toInt()
                    "%d:%02d".format(minutes, seconds)
                } else null

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (offsetText != null) "${result.sourcePlatform} Reel • Matched at $offsetText" else "${result.sourcePlatform} Reel • Song Identified",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                AsyncImage(
                    model = song.albumArtUriString,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (song.album.isNotBlank() && !song.album.equals("YouTube Music", ignoreCase = true)) {
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(20.dp))

                if (offsetText != null && offset != null && offset > 0f) {
                    val offsetMs = (offset * 1000L).toLong()
                    Button(
                        onClick = {
                            playerViewModel.playSongs(
                                songsToPlay = listOf(song),
                                startSong = song,
                                queueName = "Reel Audio ($offsetText)",
                                startPositionMs = offsetMs,
                            )
                            onDismiss()
                            playerViewModel.showPlayer()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play from $offsetText")
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            playerViewModel.playSongs(
                                songsToPlay = listOf(song),
                                startSong = song,
                                queueName = "Reel Audio",
                                startPositionMs = 0L,
                            )
                            onDismiss()
                            playerViewModel.showPlayer()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Play from beginning (0:00)")
                    }
                } else {
                    Button(
                        onClick = {
                            playerViewModel.playSongs(
                                songsToPlay = listOf(song),
                                startSong = song,
                                queueName = "Reel Audio",
                                startPositionMs = 0L,
                            )
                            onDismiss()
                            playerViewModel.showPlayer()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play Song")
                    }
                }
            } else {
                Text(
                    "Could not identify music from this Reel link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
