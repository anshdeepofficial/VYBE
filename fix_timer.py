with open('app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('isTimerActiveProvider = { isTimerActive != null },\n            onSleepTimerToggle = { showTimerBottomSheet = true }', 'isTimerActiveProvider = { isTimerActive != null },\n            timerStringProvider = { isTimerActive },\n            onSleepTimerToggle = { showTimerBottomSheet = true }')
with open('app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt', 'w', encoding='utf-8') as f:
    f.write(content)
