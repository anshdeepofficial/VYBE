with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# The class ends right before `internal fun Song.withRepositoryHydration`
marker = 'internal fun Song.withRepositoryHydration(repositorySong: Song): Song {'

function_to_add = '''
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
}
'''

content = content.replace('}\n\n' + marker, function_to_add + '\n' + marker)

with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)
