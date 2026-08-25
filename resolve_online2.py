import re

# 1. PlayerViewModel
with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'r', encoding='utf-8') as f:
    pvm_content = f.read()

resolve_func = '''
    fun resolveOnlineTrackPlaceholders() {
        viewModelScope.launch(Dispatchers.IO) {
            // Find all online tracks in cache that have "Online Track" as title
            val allCached = musicRepository.getAllCachedOnlineSongs() // We need this or we just fetch from liked songs
            val placeholders = onlineFavoriteSongs.value.filter { it.title == "Online Track" }
            
            // To be comprehensive, if the user refreshes, let's just resolve all placeholders in the UI's reach
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
        }
    }
'''
if 'fun resolveOnlineTrackPlaceholders' not in pvm_content:
    last_brace = pvm_content.rfind('}')
    pvm_content = pvm_content[:last_brace] + resolve_func + '\n}'
    with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'w', encoding='utf-8') as f:
        f.write(pvm_content)

# 2. LibraryScreen
with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt', 'r', encoding='utf-8') as f:
    ls_content = f.read()

ls_content = ls_content.replace('syncManager.incrementalSync()', 'syncManager.incrementalSync()\n            playerViewModel.resolveOnlineTrackPlaceholders()')

with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt', 'w', encoding='utf-8') as f:
    f.write(ls_content)
