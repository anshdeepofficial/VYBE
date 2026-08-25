import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\components\TimerOptionsBottomSheet.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove variables counterSliderPosition, isTimerMode, playCount parameter
content = re.sub(r'playCount:\s*Float\s*=\s*1f,', '', content)
content = re.sub(r'var counterSliderPosition by remember \{ mutableStateOf\(1f\) \}', '', content)
content = re.sub(r'var isTimerMode by remember \{ mutableStateOf\(true\) \}.*', '', content)

# 2. Fix the initial side effect
content = re.sub(r'counterSliderPosition = playCount\s*// Restore counter mode if play count was previously set\s*if \(playCount > 1f\) \{\s*isTimerMode = false\s*\}', '', content)

# 3. Fix enabled states
content = re.sub(r'enabled = isTimerMode \|\| counterSliderPosition == 1f,', 'enabled = true,', content)
content = re.sub(r'enabled = !isTimerMode \|\| timerSliderPosition == 0f,', 'enabled = false,', content)
content = re.sub(r'isTimerMode = true', '', content)

content = re.sub(r'enabled = counterSliderPosition == 1f,', 'enabled = true,', content)
content = re.sub(r'enabled = activeTimerValueDisplay != null \|\| counterSliderPosition != 1f,', 'enabled = activeTimerValueDisplay != null,', content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
