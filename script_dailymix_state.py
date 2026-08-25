import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\viewmodel\DailyMixStateHolder.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

is_refreshing = """    private val _isRefreshing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isRefreshing: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRefreshing
"""

if 'val isRefreshing:' not in content:
    content = content.replace('private val _dailyMixSongs', is_refreshing + '\n    private val _dailyMixSongs')
    
    # modify updateDailyMix
    content = content.replace('updateJob = scope?.launch(Dispatchers.IO) {', 'updateJob = scope?.launch(Dispatchers.IO) {\n            _isRefreshing.value = true')
    content = content.replace('_dailyMixSongs.value = finalDailyMix\n            _yourMixSongs.value = finalYourMix\n            _quickPickSongs.value = finalQuickPicks\n            _homeMixPreviewSongs.value = finalHomePreviews\n        }', '_dailyMixSongs.value = finalDailyMix\n            _yourMixSongs.value = finalYourMix\n            _quickPickSongs.value = finalQuickPicks\n            _homeMixPreviewSongs.value = finalHomePreviews\n            _isRefreshing.value = false\n        }')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
