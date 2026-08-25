import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\viewmodel\PlayerViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

is_refreshing_expose = """
    val isHomeRefreshing: StateFlow<Boolean> = dailyMixStateHolder.isRefreshing
"""

if 'val isHomeRefreshing' not in content:
    content = content.replace('val quickPickSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.quickPickSongs', 'val quickPickSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.quickPickSongs\n    val isHomeRefreshing: StateFlow<Boolean> = dailyMixStateHolder.isRefreshing')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
