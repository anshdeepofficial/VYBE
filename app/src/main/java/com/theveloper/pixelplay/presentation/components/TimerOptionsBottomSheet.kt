package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.compose.ui.text.style.TextOverflow

val predefinedTimes = listOf(0, 5, 10, 15, 20, 30, 45, 60) // 0 represents 'Off'

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerOptionsBottomSheet(
    onPlayCounter: (count: Int) -> Unit,
    activeTimerValueDisplay: String?,
    activeTimerDurationMinutes: Int?,
    playCount: Float,
    isEndOfTrackTimerActive: Boolean,
    isEndOfPlaylistTimerActive: Boolean,
    onDismiss: () -> Unit,
    onCancelCountedPlay: () -> Unit,
    onSetPredefinedTimer: (minutes: Int) -> Unit,
    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
    onSetEndOfPlaylistTimer: (enable: Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit
) {
    var showCustomTimePicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var timerSliderPosition by remember { mutableStateOf(0f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isSwitchEnabled = isEndOfTrackTimerActive
    val isEopSwitchEnabled = isEndOfPlaylistTimerActive

    // Animate background color
    val boxBackgroundColor by animateColorAsState(
        targetValue = if (isSwitchEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "boxBackgroundColorAnimation"
    )
    val eopBoxBackgroundColor by animateColorAsState(
        targetValue = if (isEopSwitchEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "eopBoxBackgroundColorAnimation"
    )

    // Animate corner radius
    val boxCornerRadius by animateDpAsState(
        targetValue = if (isSwitchEnabled) 18.dp else 50.dp,
        label = "boxCornerRadiusAnimation"
    )
    val eopBoxCornerRadius by animateDpAsState(
        targetValue = if (isEopSwitchEnabled) 18.dp else 50.dp,
        label = "eopBoxCornerRadiusAnimation"
    )

    LaunchedEffect(activeTimerDurationMinutes, activeTimerValueDisplay, playCount) {
        timerSliderPosition = when {
            activeTimerDurationMinutes != null -> {
                val index = predefinedTimes.indexOf(activeTimerDurationMinutes)
                if (index != -1) index.toFloat() else 0f
            }
            activeTimerValueDisplay == null -> 0f
            activeTimerValueDisplay.startsWith("Custom:") -> 0f
            else -> 0f
        }
        
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = stringResource(R.string.sleep_timer_title),
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Predefined times replaced by Slider
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                val currentIndex =
                    timerSliderPosition.roundToInt().coerceIn(0, predefinedTimes.size - 1)
                val currentMinutes = predefinedTimes[currentIndex]
                val timerDisplayText = if (currentMinutes == 0) {
                    stringResource(R.string.sleep_timer_zero_minutes_format)
                } else {
                    stringResource(R.string.sleep_timer_n_minutes_format, currentMinutes)
                }
                Text(
                    text = timerDisplayText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(
                                topEnd = 18.dp,
                                topStart = 18.dp,
                                bottomStart = 18.dp,
                                bottomEnd = 18.dp
                            )
                        )
                ) {
                    Slider(
                        value = timerSliderPosition,
                        onValueChange = {
                            timerSliderPosition = it
                            
                        },
                        valueRange = 0f..(predefinedTimes.size - 1).toFloat(),
                        steps = predefinedTimes.size - 2, // Number of discrete intervals
                        enabled = true,
                        onValueChangeFinished = {
                            val selectedIndexOnFinish = timerSliderPosition.roundToInt()
                                .coerceIn(0, predefinedTimes.size - 1)
                            val selectedMinutesOnFinish = predefinedTimes[selectedIndexOnFinish]
                            if (selectedMinutesOnFinish == 0) {
                                if (activeTimerDurationMinutes != null) {
                                    onCancelTimer()
                                }
                            } else {
                                onSetPredefinedTimer(selectedMinutesOnFinish)
                            }
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier
                                    .heightIn(min = 32.dp),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(
                                bottomEnd = 18.dp,
                                bottomStart = 18.dp,
                                topEnd = 6.dp,
                                topStart = 6.dp
                            )
                        )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            

            // End of playlist option
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp)
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusBL = eopBoxCornerRadius,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusTR = eopBoxCornerRadius,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTL = eopBoxCornerRadius,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = eopBoxCornerRadius,
                            smoothnessAsPercentTR = 60
                        )
                    ) // Apply animated corner radius for clipping
                    .background(color = eopBoxBackgroundColor)   // Apply animated background color
                    .clickable(
                        enabled = true,
                        onClick = {
                            onSetEndOfPlaylistTimer(!isEopSwitchEnabled)
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "End of playlist",
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        color = if (isEopSwitchEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface // Adjust text color for contrast
                    )
                    Switch(
                        checked = isEopSwitchEnabled,
                        enabled = true,
                        onCheckedChange = {
                            onSetEndOfPlaylistTimer(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        thumbContent = if (isEopSwitchEnabled) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.sleep_timer_cd_switch_on),
                                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // End of track option
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp)
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusBL = boxCornerRadius,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusTR = boxCornerRadius,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTL = boxCornerRadius,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = boxCornerRadius,
                            smoothnessAsPercentTR = 60
                        )
                    ) // Apply animated corner radius for clipping
                    .background(color = boxBackgroundColor)   // Apply animated background color
                    .clickable(
                        enabled = true,
                        onClick = {
                            onSetEndOfTrackTimer(!isSwitchEnabled)
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_end_of_current_track),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        color = if (isSwitchEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface // Adjust text color for contrast
                    )
                    Switch(
                        checked = isSwitchEnabled,
                        enabled = true,
                        onCheckedChange = {
                            onSetEndOfTrackTimer(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        thumbContent = if (isSwitchEnabled) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = stringResource(R.string.sleep_timer_cd_switch_on),
                                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }

            val buttonHeight = 68.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showCustomTimePicker = true },
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        bottomStart = 50.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight)
                ) {
                    Text(stringResource(R.string.sleep_timer_custom_time), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = {
                        onCancelTimer()
                        onSetEndOfTrackTimer(false)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight)
                ) {
                    Text(stringResource(R.string.sleep_timer_cancel_timer), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = {
                        val currentIndex = timerSliderPosition.roundToInt().coerceIn(0, predefinedTimes.size - 1)
                        val selectedMinutesOnFinish = predefinedTimes[currentIndex]
                        if (selectedMinutesOnFinish > 0) {
                            onSetPredefinedTimer(selectedMinutesOnFinish)
                        } else {
                            onCancelTimer()
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(
                        topStart = 8.dp,
                        bottomStart = 8.dp,
                        topEnd = 50.dp,
                        bottomEnd = 50.dp
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight)
                ) {
                    Text("Apply Timer", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    

    if (showCustomTimePicker) {
        val initialHour = 0
        val initialMinute = 15

        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = {
                showCustomTimePicker = false
            },
            title = { Text(stringResource(R.string.sleep_timer_ui_set_custom_duration)) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hour = timePickerState.hour
                        val minute = timePickerState.minute
                        val totalMinutes = hour * 60 + minute

                        if (totalMinutes > 0) {
                            onSetPredefinedTimer(totalMinutes)
                        }
                        showCustomTimePicker = false
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerOptionsBottomSheet(
    playerViewModel: com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel,
    onDismiss: () -> Unit
) {
    val activeTimerValueDisplay by playerViewModel.activeTimerValueDisplay.collectAsStateWithLifecycle()
    val activeTimerDurationMinutes by playerViewModel.activeTimerDurationMinutes.collectAsStateWithLifecycle()
    val playCount by playerViewModel.playCount.collectAsStateWithLifecycle()
    val isEndOfTrackTimerActive by playerViewModel.isEndOfTrackTimerActive.collectAsStateWithLifecycle()
    val isEndOfPlaylistTimerActive by playerViewModel.isEndOfPlaylistTimerActive.collectAsStateWithLifecycle()

    TimerOptionsBottomSheet(
        onPlayCounter = { playerViewModel.playCounted(it) },
        activeTimerValueDisplay = activeTimerValueDisplay,
        activeTimerDurationMinutes = activeTimerDurationMinutes,
        playCount = playCount,
        isEndOfTrackTimerActive = isEndOfTrackTimerActive,
        isEndOfPlaylistTimerActive = isEndOfPlaylistTimerActive,
        onDismiss = onDismiss,
        onCancelCountedPlay = { playerViewModel.cancelCountedPlay() },
        onSetPredefinedTimer = { playerViewModel.setSleepTimer(it) },
        onSetEndOfTrackTimer = { playerViewModel.setEndOfTrackTimer(it) },
        onSetEndOfPlaylistTimer = { playerViewModel.setEndOfPlaylistTimer(it) },
        onOpenCustomTimePicker = {},
        onCancelTimer = { playerViewModel.cancelSleepTimer() }
    )
}

