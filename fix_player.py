with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('val allCached = musicRepository.getAllCachedOnlineSongs() // We need this or we just fetch from liked songs\n', '')
with open('app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlayerViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)
