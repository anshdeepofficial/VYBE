import re

with open('app/src/main/java/com/theveloper/pixelplay/presentation/components/TimerOptionsBottomSheet.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove Counted Play Column
content = re.sub(r'Column\(modifier = Modifier\.fillMaxWidth\(\)\) \{\s*val currentPlayCount = 1.*?\n            \}\s*Spacer\(modifier = Modifier\.height\(16\.dp\)\)', '', content, flags=re.DOTALL)

# Replace the Buttons row
button_row_regex = r'val buttonHeight = 68\.dp\s*Row\(\s*modifier = Modifier\s*\.fillMaxWidth\(\)\s*\.padding\(vertical = 16\.dp, horizontal = 6\.dp\),\s*horizontalArrangement = Arrangement\.spacedBy\(6\.dp\),\s*verticalAlignment = Alignment\.CenterVertically\s*\)\s*\{.*?\n            \}'

apply_button = '''val buttonHeight = 68.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(buttonHeight)
                ) {
                    Text("Apply Timer")
                }
            }'''

content = re.sub(button_row_regex, apply_button, content, flags=re.DOTALL)

# Let's remove the TimePicker dialog as well, since Custom Time button is gone.
content = re.sub(r'if \(showCustomTimePicker\) \{.*?\n    \}', '', content, flags=re.DOTALL)

with open('app/src/main/java/com/theveloper/pixelplay/presentation/components/TimerOptionsBottomSheet.kt', 'w', encoding='utf-8') as f:
    f.write(content)
