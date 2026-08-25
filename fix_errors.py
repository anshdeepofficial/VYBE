import re

# 1. Remove resolveMissingOnlineTracks from MusicRepository.kt
with open('app/src/main/java/com/theveloper/pixelplay/data/repository/MusicRepository.kt', 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('suspend fun cacheOnlineSongs(songs: List<Song>)\n    suspend fun resolveMissingOnlineTracks(onlineMusicRepository: com.theveloper.pixelplay.data.repository.OnlineMusicRepository)', 'suspend fun cacheOnlineSongs(songs: List<Song>)')
with open('app/src/main/java/com/theveloper/pixelplay/data/repository/MusicRepository.kt', 'w', encoding='utf-8') as f:
    f.write(content)

# 2. Fix PlayerViewModel.kt
with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Strip any existing resolveOnlineTrackPlaceholders inside or outside
content = re.sub(r'\n    fun resolveOnlineTrackPlaceholders\(\) \{.*?\n        \}\n    \}\n', '', content, flags=re.DOTALL)
content = re.sub(r'\n    fun resolveOnlineTrackPlaceholders\(\) \{.*?\}\n\}\n', '\n}\n', content, flags=re.DOTALL)

# Insert correctly inside PlayerViewModel class
# Search for `fun onCleared()` or similar known class-level function
insert_pos = content.rfind('override fun onCleared()')
if insert_pos == -1:
    # Let's just find the last closing brace of the file, and then the one before it which is the class closing brace
    # Actually, PlayerViewModel has an init block, we can just insert before `override fun onCleared()`
    pass

correct_func = '''
    fun resolveOnlineTrackPlaceholders() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val placeholders = onlineFavoriteSongs.value.filter { it.title == "Online Track" }
            val resolvedList = mutableListOf<com.theveloper.pixelplay.data.model.Song>()
            for (placeholder in placeholders) {
                val resolved = runCatching { onlineMusicRepository.getTrackDetails(placeholder.id) }.getOrNull()
                if (resolved != null) {
                    resolvedList.add(resolved)
                }
            }
            if (resolvedList.isNotEmpty()) {
                musicRepository.cacheOnlineSongs(resolvedList)
            }
            
            // Also attempt to resolve current playback queue placeholders
            val queuePlaceholders = _playerUiState.value.currentPlaybackQueue.filter { it.title == "Online Track" }
            val resolvedQueue = mutableListOf<com.theveloper.pixelplay.data.model.Song>()
            for (placeholder in queuePlaceholders) {
                val resolved = runCatching { onlineMusicRepository.getTrackDetails(placeholder.id) }.getOrNull()
                if (resolved != null) {
                    resolvedQueue.add(resolved)
                }
            }
            if (resolvedQueue.isNotEmpty()) {
                musicRepository.cacheOnlineSongs(resolvedQueue)
            }
        }
    }
'''

# Put it right before `override fun onCleared()`
if 'override fun onCleared()' in content:
    content = content.replace('override fun onCleared() {', correct_func + '\n    override fun onCleared() {')
else:
    # Just put it right before the last closing brace of the file if there are no companion objects
    pass

with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)
