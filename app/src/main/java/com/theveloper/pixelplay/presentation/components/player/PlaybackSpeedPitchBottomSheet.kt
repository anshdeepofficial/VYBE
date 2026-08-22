package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import java.util.Locale
import kotlin.math.roundToInt

private val SPEED_PRESETS = listOf(0.5f, 0.75f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedPitchBottomSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val currentSpeed by playerViewModel.playbackSpeed.collectAsStateWithLifecycle()
    val currentPitch by playerViewModel.playbackPitch.collectAsStateWithLifecycle()

    var speedValue by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    var pitchValue by remember(currentPitch) { mutableFloatStateOf(currentPitch) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed & Pitch",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        speedValue = 1.0f
                        pitchValue = 1.0f
                        playerViewModel.setPlaybackParameters(1.0f, 1.0f)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Speed Slider ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playback Speed",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = String.format(Locale.US, "%.2fx", speedValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = speedValue,
                onValueChange = {
                    val rounded = (it * 20).roundToInt() / 20f
                    speedValue = rounded
                    playerViewModel.setPlaybackParameters(rounded, pitchValue)
                },
                valueRange = 0.5f..2.0f,
                steps = 29,
                modifier = Modifier.fillMaxWidth()
            )

            // Speed Presets
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(SPEED_PRESETS) { preset ->
                    val isSelected = (speedValue - preset).let { if (it < 0) -it else it } < 0.02f
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            speedValue = preset
                            playerViewModel.setPlaybackParameters(preset, pitchValue)
                        },
                        label = {
                            Text(
                                text = String.format(Locale.US, "%.2fx", preset),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Pitch Slider ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Pitch",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = String.format(Locale.US, "%.2fx", pitchValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Slider(
                value = pitchValue,
                onValueChange = {
                    val rounded = (it * 20).roundToInt() / 20f
                    pitchValue = rounded
                    playerViewModel.setPlaybackParameters(speedValue, rounded)
                },
                valueRange = 0.5f..1.5f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
