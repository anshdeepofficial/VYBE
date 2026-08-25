import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\screens\SearchScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('import clickable\\n', 'import androidx.compose.foundation.clickable\\n')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
