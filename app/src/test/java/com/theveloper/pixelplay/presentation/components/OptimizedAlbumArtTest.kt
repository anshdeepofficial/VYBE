package com.theveloper.pixelplay.presentation.components

import coil.size.Dimension
import coil.size.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OptimizedAlbumArtTest {

    @Test
    fun safeAlbumArtTargetSize_clampsOriginalRequests() {
        val targetSize = safeAlbumArtTargetSize(Size.ORIGINAL)

        assertThat((targetSize.width as Dimension.Pixels).px)
            .isEqualTo(MaxSafeAlbumArtDimensionPx)
        assertThat((targetSize.height as Dimension.Pixels).px)
            .isEqualTo(MaxSafeAlbumArtDimensionPx)
    }

    @Test
    fun safeAlbumArtTargetSize_keepsBoundedRequests() {
        val targetSize = Size(800, 600)

        assertThat(safeAlbumArtTargetSize(targetSize)).isEqualTo(targetSize)
    }

    @Test
    fun upgradeRemoteArtworkModel_requestsSelectedGoogleArtworkResolution() {
        val source = "https://lh3.googleusercontent.com/example=w226-h226-l90-rj"

        assertThat(upgradeRemoteArtworkModel(source, Size(256, 256)))
            .isEqualTo("https://lh3.googleusercontent.com/example=w256-h256-l90-rj")
        assertThat(upgradeRemoteArtworkModel(source, SafeOriginalAlbumArtSize))
            .isEqualTo("https://lh3.googleusercontent.com/example=w2048-h2048-l90-rj")
    }

    @Test
    fun upgradeRemoteArtworkUrl_requestsFullResolutionYouTubeThumbnail() {
        val source = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"

        assertThat(upgradeRemoteArtworkUrl(source, MaxSafeAlbumArtDimensionPx))
            .isEqualTo("https://i.ytimg.com/vi/abc123/maxresdefault.jpg")
    }

    @Test
    fun upgradeRemoteArtworkUrl_preservesUnknownAndLocalSources() {
        val unknownRemote = "https://covers.example.com/art-120.jpg"
        val local = "pixelplay_local_art://42"

        assertThat(upgradeRemoteArtworkUrl(unknownRemote, 2048)).isEqualTo(unknownRemote)
        assertThat(upgradeRemoteArtworkModel(local, SafeOriginalAlbumArtSize)).isEqualTo(local)
    }
}
