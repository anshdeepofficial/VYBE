import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\viewmodel\PlaybackDispatchStateHolder.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

play_songs_new = """    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        com.theveloper.pixelplay.utils.PerformanceTracker.start()
        cancelPendingFullQueuePlayback()"""
        
play_shuffled_new = """    fun playSongsShuffled(
        songsToPlay: List<Song>,
        queueName: String = "None",
        playlistId: String? = null,
        startAtZero: Boolean = false
    ) {
        com.theveloper.pixelplay.utils.PerformanceTracker.start()
        cancelPendingFullQueuePlayback()"""

content = content.replace("""    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        cancelPendingFullQueuePlayback()""", play_songs_new)
        
content = content.replace("""    fun playSongsShuffled(
        songsToPlay: List<Song>,
        queueName: String = "None",
        playlistId: String? = null,
        startAtZero: Boolean = false
    ) {
        cancelPendingFullQueuePlayback()""", play_shuffled_new)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
