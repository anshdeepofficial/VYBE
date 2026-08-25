with open('app/src/main/java/com/theveloper/pixelplay/data/service/MusicService.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('stopForeground(STOP_FOREGROUND_REMOVE)', 'stopForeground(STOP_FOREGROUND_REMOVE)\n                    cancelTemporaryForegroundNotification()')
with open('app/src/main/java/com/theveloper/pixelplay/data/service/MusicService.kt', 'w', encoding='utf-8') as f:
    f.write(content)
