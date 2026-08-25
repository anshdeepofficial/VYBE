with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Clean the end of the file starting from parsePersistedLyrics
marker = 'internal fun parsePersistedLyrics'
pos = content.find(marker)
content = content[:pos]

# Put parsePersistedLyrics back
clean_end = '''internal fun parsePersistedLyrics(rawLyrics: String?): Lyrics? {
    val normalizedLyrics = rawLyrics?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parsedLyrics = LyricsUtils.parseLyrics(normalizedLyrics)
    return parsedLyrics.takeIf {
        !it.synced.isNullOrEmpty() || !it.plain.isNullOrEmpty()
    }
}
'''
content += clean_end

# 2. Insert resolveOnlineTrackPlaceholders right before the final closing brace of PlayerViewModel
class_end_marker = '    fun addCustomGenre(genre: String, iconResId: Int? = null) {\n        viewModelScope.launch {\n            userPreferencesRepository.addCustomGenre(genre, iconResId)\n        }\n    }\n}'
if class_end_marker in content:
    func_to_insert = '''
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
    content = content.replace(class_end_marker, class_end_marker[:-1] + func_to_insert + '}')
else:
    print("Error: Could not find class_end_marker")

with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)
