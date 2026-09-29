package com.theveloper.pixelplay.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Compatibility baseline for existing saved queues before identity migration. */
class SavedPlaybackQueueTest {
    @Test
    fun `legacy saved queue without optional fields still loads`() {
        val restored = Json.decodeFromString<SavedPlaybackQueue>(
            """{
                "id":"saved-queue", "name":"My queue", "savedAtEpochMs":1,
                "songs":[{
                    "id":"yt_video-id", "title":"Track", "artist":"Artist",
                    "artistId":-1, "album":"Album", "albumId":-1,
                    "path":"", "contentUriString":"yt://video-id", "duration":180000
                }]
            }""".trimIndent()
        )

        val song = restored.songs.single().toSong()
        assertEquals("yt_video-id", song.id)
        assertEquals("yt://video-id", song.contentUriString)
        assertEquals("Track", song.title)
        assertEquals(180_000L, song.duration)
        assertNull(restored.currentSongId)
    }

    @Test
    fun `serialization keeps intentional repeats and selected track identity`() {
        val first = song("yt_first", "yt://first")
        val second = song("yt_second", "yt://second")
        val queue = SavedPlaybackQueue(
            id = "queue", name = "Manual queue",
            songs = listOf(first, second, first).map { it.toSavedQueueSong() },
            currentSongId = second.id, savedAtEpochMs = 123L,
        )

        val restored = Json.decodeFromString<SavedPlaybackQueue>(Json.encodeToString(queue))

        assertEquals(queue, restored)
        assertEquals(listOf(first.id, second.id, first.id), restored.songs.map { it.toSong().id })
        assertEquals(second.id, restored.currentSongId)
    }

    @Test
    fun `local queue restore retains local URI without inventing online identity`() {
        val local = song("42", "content://media/external/audio/media/42")
            .copy(path = "/storage/emulated/0/Music/track.flac", mimeType = "audio/flac")

        val restored = local.toSavedQueueSong().toSong()

        assertEquals(local.id, restored.id)
        assertEquals(local.contentUriString, restored.contentUriString)
        assertEquals(local.path, restored.path)
        assertEquals(local.mimeType, restored.mimeType)
    }

    private fun song(id: String, uri: String) = Song(
        id = id, title = "Track", artist = "Artist", artistId = -1L,
        album = "Album", albumId = -1L, path = "", contentUriString = uri,
        albumArtUriString = null, duration = 180_000L,
        mimeType = null, bitrate = null, sampleRate = null,
    )
}
