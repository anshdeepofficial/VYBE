with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('val onRefresh: () -> Unit = remember(scope, syncManager) {', 'val onRefresh: () -> Unit = remember(scope, syncManager, playerViewModel) {')
with open('app/src/main/java/com/theveloper/pixelplay/presentation/screens/LibraryScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
