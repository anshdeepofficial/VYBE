package com.theveloper.pixelplay.presentation.model

import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RecentlyPlayedSongUiTest {
    @Test
    fun `restores streamed song from persisted history snapshot when library is empty`() {
        val snapshot = PlaybackStatsRepository.PlaybackTrackSnapshot(
            title = "Powerhouse",
            artist = "Test Artist",
            album = "Test Album",
            artworkUri = "https://example.test/art.jpg",
            contentUri = "yt://abcdefghijk",
            path = "yt_abcdefghijk",
            durationMs = 180_000L,
            artistId = 12L,
            albumId = 34L,
            genre = "Punjabi",
            mimeType = "audio/mp4",
            provider = "YOUTUBE_MUSIC",
            resultType = "SONG",
        )

        val result = mapRecentlyPlayedSongs(
            playbackHistory = listOf(
                PlaybackStatsRepository.PlaybackHistoryEntry(
                    songId = "yt_abcdefghijk",
                    timestamp = 1_000L,
                    track = snapshot,
                )
            ),
            songs = emptyList(),
            nowMillis = 2_000L,
        )

        assertEquals(1, result.size)
        assertEquals("Powerhouse", result.single().song.title)
        assertEquals("Test Artist", result.single().song.artist)
        assertNotNull(result.single().song.albumArtUriString)
        assertEquals("yt_abcdefghijk", result.single().song.id)
    }
}
