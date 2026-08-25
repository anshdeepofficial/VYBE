import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\components\GradientTopBar.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('androidx.compose.material.icons.rounded.Refresh', 'Icons.Rounded.Refresh')
content = content.replace('import androidx.compose.material.icons.rounded.Cloud', 'import androidx.compose.material.icons.rounded.Cloud\\nimport androidx.compose.material.icons.rounded.Refresh')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
