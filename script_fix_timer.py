import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\components\TimerOptionsBottomSheet.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace all occurrences of counterSliderPosition with 1f to fix syntax in remaining lines if any
content = re.sub(r'val currentPlayCount = counterSliderPosition\.toInt\(\)', 'val currentPlayCount = 1', content)
content = re.sub(r'counterSliderPosition = it', '', content)
content = re.sub(r'counterSliderPosition = playCount', '', content)
content = re.sub(r'counterSliderPosition', '1f', content)
content = re.sub(r'isTimerMode = false', '', content)
content = re.sub(r'isTimerMode = true', '', content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
