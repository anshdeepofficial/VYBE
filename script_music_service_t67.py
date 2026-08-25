import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\data\service\MusicService.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

playback_state_old = """        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.tag(TAG).d("Playback state changed: ")
            if (playbackState == Player.STATE_ENDED) {"""
playback_state_new = """        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.tag(TAG).d("Playback state changed: ")
            if (playbackState == Player.STATE_BUFFERING) {
                com.theveloper.pixelplay.utils.PerformanceTracker.markT6()
            } else if (playbackState == Player.STATE_READY) {
                com.theveloper.pixelplay.utils.PerformanceTracker.markT7()
            }
            
            if (playbackState == Player.STATE_ENDED) {"""

content = content.replace(playback_state_old, playback_state_new)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
