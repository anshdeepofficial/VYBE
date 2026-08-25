import re

with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Remove the broken function
broken_func_pattern = r'\n    fun resolveOnlineTrackPlaceholders\(\) \{.*?\}\n'
content = re.sub(broken_func_pattern, '', content, flags=re.DOTALL)

# Find the end of PlayerViewModel class.
# PlayerViewModel class has:
# class PlayerViewModel @Inject constructor(...) : ViewModel(), ... {
# ...
# }
# internal fun shouldForceResetPlayer(previousSong: Song, contentUriString: String): Boolean { ... }
# internal fun parsePersistedLyrics(rawLyrics: String?): Lyrics? { ... }

# The class ends just before `internal fun shouldForceResetPlayer`
insert_pos = content.find('internal fun shouldForceResetPlayer')
if insert_pos == -1:
    insert_pos = content.find('internal fun parsePersistedLyrics')

brace_pos = content.rfind('}', 0, insert_pos)

correct_func = '''
    fun resolveOnlineTrackPlaceholders() {
        viewModelScope.launch(Dispatchers.IO) {
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

content = content[:brace_pos] + correct_func + content[brace_pos:]

with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)
