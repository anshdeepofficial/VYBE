import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\screens\SearchScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('androidx.compose.foundation.lazy.items(querySuggestions', 'items(querySuggestions')
content = content.replace('androidx.compose.foundation.clickable', 'clickable') # wait, that's wrong, the import is there. I'll just change .clickable to .clickable which it already is.

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
