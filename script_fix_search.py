import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\screens\SearchScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

imports = [
    'import androidx.compose.foundation.lazy.items',
    'import androidx.compose.foundation.clickable',
    'import androidx.compose.foundation.layout.heightIn',
    'import androidx.compose.foundation.layout.fillMaxWidth'
]

for imp in imports:
    if imp not in content:
        content = content.replace('import androidx.compose.foundation.layout.padding', f'{imp}\\nimport androidx.compose.foundation.layout.padding')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
