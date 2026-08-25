import re

file_path = r'app\src\main\java\com\theveloper\pixelplay\presentation\viewmodel\PlaybackDispatchStateHolder.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# T2 and T3 during buildResolvedPlaybackMediaItem
build_item_old = """    suspend fun buildResolvedPlaybackMediaItem(song: Song): MediaItem {
        val mediaItem = MediaItemBuilder.build(song)"""
build_item_new = """    suspend fun buildResolvedPlaybackMediaItem(song: Song): MediaItem {
        com.theveloper.pixelplay.utils.PerformanceTracker.markT2()
        val mediaItem = MediaItemBuilder.build(song)"""
content = content.replace(build_item_old, build_item_new)

# end of buildResolvedPlaybackMediaItem
end_build_old = """            val durationMillis = onlineMusicRepository.getDurationInMillis(song.id) ?: 0L
            builder.setUri(mediaUri).setMediaMetadata(
                originalMetadata.buildUpon()
                    .putString("durationMs", durationMillis.toString())
                    .build()
            ).build()
        }
    }"""
end_build_new = """            val durationMillis = onlineMusicRepository.getDurationInMillis(song.id) ?: 0L
            val finalItem = builder.setUri(mediaUri).setMediaMetadata(
                originalMetadata.buildUpon()
                    .putString("durationMs", durationMillis.toString())
                    .build()
            ).build()
            com.theveloper.pixelplay.utils.PerformanceTracker.markT3()
            return finalItem
        }
    }"""
content = content.replace(end_build_old, end_build_new)

# T1 when internalPlaySongs begins
internal_play_old = """    private suspend fun internalPlaySongs(
        songsToPlay: List<Song>,
        startSong: Song,
        queueName: String = "None",
        playlistId: String? = null
    ) {"""
internal_play_new = """    private suspend fun internalPlaySongs(
        songsToPlay: List<Song>,
        startSong: Song,
        queueName: String = "None",
        playlistId: String? = null
    ) {
        com.theveloper.pixelplay.utils.PerformanceTracker.markT1()"""
content = content.replace(internal_play_old, internal_play_new)

# T4/T5 during playSongsAction
play_songs_action_old = """            val playSongsAction: () -> Unit = {
                cb.scope.launch(Dispatchers.Main.immediate) {
                    // Use Direct Engine Access to avoid TransactionTooLargeException on Binder
                    dualPlayerEngine.cancelNext()
                    val enginePlayer = dualPlayerEngine.masterPlayer

                    enginePlayer.setMediaItem(startMediaItem, 0L)
                    enginePlayer.prepare()"""
play_songs_action_new = """            val playSongsAction: () -> Unit = {
                cb.scope.launch(Dispatchers.Main.immediate) {
                    // Use Direct Engine Access to avoid TransactionTooLargeException on Binder
                    dualPlayerEngine.cancelNext()
                    val enginePlayer = dualPlayerEngine.masterPlayer

                    com.theveloper.pixelplay.utils.PerformanceTracker.markT4()
                    enginePlayer.setMediaItem(startMediaItem, 0L)
                    com.theveloper.pixelplay.utils.PerformanceTracker.markT5()
                    enginePlayer.prepare()"""
content = content.replace(play_songs_action_old, play_songs_action_new)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
