package com.theveloper.pixelplay.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedQueueSong(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val path: String,
    val contentUriString: String,
    val albumArtUriString: String? = null,
    val duration: Long,
    val genre: String? = null,
    val mimeType: String? = null,
    val telegramFileId: Int? = null,
    val telegramChatId: Long? = null,
    val neteaseId: Long? = null,
    val gdriveFileId: String? = null,
    val qqMusicMid: String? = null,
    val navidromeId: String? = null,
    val jellyfinId: String? = null,
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        path = path,
        contentUriString = contentUriString,
        albumArtUriString = albumArtUriString,
        duration = duration,
        genre = genre,
        mimeType = mimeType,
        bitrate = null,
        sampleRate = null,
        telegramFileId = telegramFileId,
        telegramChatId = telegramChatId,
        neteaseId = neteaseId,
        gdriveFileId = gdriveFileId,
        qqMusicMid = qqMusicMid,
        navidromeId = navidromeId,
        jellyfinId = jellyfinId,
    )
}

fun Song.toSavedQueueSong(): SavedQueueSong = SavedQueueSong(
    id = id,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    path = path,
    contentUriString = contentUriString,
    albumArtUriString = albumArtUriString,
    duration = duration,
    genre = genre,
    mimeType = mimeType,
    telegramFileId = telegramFileId,
    telegramChatId = telegramChatId,
    neteaseId = neteaseId,
    gdriveFileId = gdriveFileId,
    qqMusicMid = qqMusicMid,
    navidromeId = navidromeId,
    jellyfinId = jellyfinId,
)

@Serializable
data class SavedPlaybackQueue(
    val id: String,
    val name: String,
    val songs: List<SavedQueueSong>,
    val currentSongId: String? = null,
    val savedAtEpochMs: Long = System.currentTimeMillis(),
)
