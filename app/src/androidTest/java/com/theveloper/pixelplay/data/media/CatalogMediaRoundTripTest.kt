package com.theveloper.pixelplay.data.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.catalogMetadata
import com.theveloper.pixelplay.utils.MediaItemBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogMediaRoundTripTest {
    @Test
    fun nativeNavigationMetadataSurvivesMedia3ControllerRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = Song(
            id = "yt_abcdefghijk", title = "Recording", artist = "Artist", artistId = 1L,
            artists = listOf(ArtistRef(1L, "Artist", true, "UCartist")),
            album = "Release", albumId = 2L, remoteAlbumBrowseId = "MPRErelease",
            path = "yt://abcdefghijk", contentUriString = "yt://abcdefghijk",
            albumArtUriString = null, duration = 180000L, mimeType = "audio/mp4",
            bitrate = null, sampleRate = null,
        )
        // Real Bundle/Media3 classes, not Android JVM default-return stubs.
        val restored = MediaMapper(context).resolveSongFromMediaItem(MediaItemBuilder.build(original))
        assertNotNull(restored)
        assertEquals(original.catalogMetadata(), restored!!.catalogMetadata())
        assertEquals(original.title, restored.title)
        assertEquals(original.contentUriString, restored.contentUriString)
    }
}
