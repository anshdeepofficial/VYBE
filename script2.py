import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\components\TimerOptionsBottomSheet.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Try to find the block for the counter slider
# It starts with: val currentPlayCount = counterSliderPosition.toInt()
# Actually, I'll just use a targeted regex

pattern = r'Column\(modifier = Modifier\.fillMaxWidth\(\)\) \{\s*val currentPlayCount = .*?// Apply animated background color'

match = re.search(pattern, content, flags=re.DOTALL)
if match:
    print("Found block to delete")
else:
    print("Not found")

