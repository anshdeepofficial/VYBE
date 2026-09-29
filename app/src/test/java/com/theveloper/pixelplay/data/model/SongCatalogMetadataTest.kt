package com.theveloper.pixelplay.data.model

import com.theveloper.pixelplay.data.database.DownloadedSongEntity
import com.theveloper.pixelplay.data.database.toOnlineSongCacheEntity
import com.theveloper.pixelplay.data.database.toSong
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SongCatalogMetadataTest {
    private val track = Song(
        id = "yt_abcdefghijk", title = "Recording", artist = "First artist", artistId = 7L,
        artists = listOf(
            ArtistRef(7L, "First artist", true, "UCfirst"),
            ArtistRef(8L, "Second artist", false, "UCsecond"),
        ),
        album = "Release", albumId = 9L, albumArtist = "First artist",
        remoteAlbumBrowseId = "MPRErelease", releaseDateEpochMillis = 123456L,
        year = 2026, trackNumber = 2, discNumber = 1, isMusicVideo = true,
        path = "yt://abcdefghijk", contentUriString = "yt://abcdefghijk",
        albumArtUriString = null, duration = 180000L, mimeType = "audio/mp4",
        bitrate = null, sampleRate = null,
    )

    @Test
    fun `saved queue round trip retains all catalogue navigation metadata`() {
        val saved = track.toSavedQueueSong()
        val restored = Json.decodeFromString<SavedQueueSong>(Json.encodeToString(saved)).toSong()
        assertEquals(track.catalogMetadata(), restored.catalogMetadata())
        assertEquals(track.contentUriString, restored.contentUriString)
    }

    @Test
    fun `online cache retains catalogue metadata`() {
        val restored = track.toOnlineSongCacheEntity().toSong()
        assertEquals(track.catalogMetadata(), restored.catalogMetadata())
        assertEquals(track.title, restored.title)
    }

    @Test
    fun `download restores catalogue identity while preserving the offline file`() {
        val downloaded = DownloadedSongEntity(
            id = track.id, title = track.title, artist = track.artist,
            localFilePath = "/downloads/recording.m4a",
            catalogMetadataJson = track.catalogMetadata().encode(),
        ).toSong()
        assertEquals(track.catalogMetadata(), downloaded.catalogMetadata())
        assertEquals("/downloads/recording.m4a", downloaded.contentUriString)
    }

    @Test
    fun `snapshot serialization preserves native IDs`() {
        val snapshot = PlaybackQueueSnapshot(items = listOf(PlaybackQueueItemSnapshot(
            mediaId = track.id, uri = track.contentUriString,
            catalogMetadata = track.catalogMetadata(),
        )))
        val restored = Json.decodeFromString<PlaybackQueueSnapshot>(Json.encodeToString(snapshot))
        assertEquals(track.catalogMetadata(), restored.items.single().catalogMetadata)
    }

    @Test
    fun `metadata cannot overwrite a different recording`() {
        val other = track.copy(id = "yt_other", remoteAlbumBrowseId = "MPREother")
        assertSame(other, track.catalogMetadata().applyTo(other))
    }

    @Test
    fun `optional malformed metadata falls back without losing playable row`() {
        assertNull(SongCatalogMetadata.decode("{broken"))
        val cached = track.toOnlineSongCacheEntity().copy(catalogMetadataJson = "{broken").toSong()
        assertEquals(track.id, cached.id)
        assertEquals(track.title, cached.title)
        assertEquals(track.contentUriString, cached.contentUriString)
    }

    @Test
    fun `future metadata fields do not break current readers`() {
        val json = track.catalogMetadata().encode().dropLast(1) + ",\"future\":true}"
        assertEquals(track.catalogMetadata(), SongCatalogMetadata.decode(json))
    }
}
