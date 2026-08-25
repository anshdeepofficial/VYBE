with open('app/src/main/java/com/theveloper/pixelplay/data/service/player/DualPlayerEngine.kt', 'r', encoding='utf-8') as f:
    content = f.read()

replacement = """        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                Timber.tag("TransitionDebug").d("User manually paused playback. Clearing isFocusLossPause.")
                isFocusLossPause = false
            }
            if (playWhenReady) {"""

content = content.replace('        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {\n            if (playWhenReady) {', replacement)

with open('app/src/main/java/com/theveloper/pixelplay/data/service/player/DualPlayerEngine.kt', 'w', encoding='utf-8') as f:
    f.write(content)
