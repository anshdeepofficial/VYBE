import re

with open('app/src/main/java/com/theveloper/pixelplay/presentation/components/TimerOptionsBottomSheet.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the single Apply button with 3 buttons
single_apply_regex = r'val buttonHeight = 68\.dp\s*Row\(\s*modifier = Modifier\s*\.fillMaxWidth\(\)\s*\.padding\(vertical = 16\.dp, horizontal = 6\.dp\),\s*horizontalArrangement = Arrangement\.spacedBy\(6\.dp\),\s*verticalAlignment = Alignment\.CenterVertically\s*\)\s*\{.*?\n            \}'

three_buttons = '''val buttonHeight = 68.dp
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
            }'''

content = re.sub(single_apply_regex, three_buttons, content, flags=re.DOTALL)

# Add back TimePicker if it was removed
time_picker_code = '''
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
'''
if "if (showCustomTimePicker)" not in content:
    # Insert it right before the last closing brace of TimerOptionsBottomSheet
    last_brace_idx = content.rfind('}')
    content = content[:last_brace_idx] + time_picker_code + content[last_brace_idx+1:]

with open('app/src/main/java/com/theveloper/pixelplay/presentation/components/TimerOptionsBottomSheet.kt', 'w', encoding='utf-8') as f:
    f.write(content)
