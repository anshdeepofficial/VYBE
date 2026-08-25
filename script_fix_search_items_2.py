import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\screens\SearchScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('items(querySuggestions, key = { it }) { suggestion ->', 'items(items = querySuggestions) { suggestion ->')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
