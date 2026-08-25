import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\screens\SearchScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('import androidx.compose.foundation.clickable\\nimport androidx.compose.foundation.layout.padding', 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.layout.padding')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
