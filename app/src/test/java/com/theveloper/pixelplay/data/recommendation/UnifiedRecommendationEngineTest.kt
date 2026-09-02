package com.theveloper.pixelplay.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Test

class UnifiedRecommendationEngineTest {
    private val engine = UnifiedRecommendationEngine()

    @Test
    fun blockedArtistsAreNeverReturned() {
        val result = engine.rank(
            candidates = listOf(song("allowed", "Artist A"), song("blocked", "Artist B")),
            profile = RecommendationProfile(blockedArtists = setOf("artist b")),
            surface = RecommendationSurface.HOME,
        )
        assertThat(result.map(Song::id)).containsExactly("allowed")
    }

    @Test
    fun preferredArtistOutranksProviderOrderOnPersonalisedShelf() {
        val result = engine.rank(
            candidates = listOf(song("first", "Artist A"), song("preferred", "Artist B")),
            profile = RecommendationProfile(preferredArtists = setOf("artist b")),
            surface = RecommendationSurface.HOME,
        )
        assertThat(result.first().id).isEqualTo("preferred")
    }

    @Test
    fun duplicateProviderIdsAreCollapsed() {
        val result = engine.rank(
            candidates = listOf(song("same", "Artist A"), song("same", "Artist A")),
            profile = RecommendationProfile(),
            surface = RecommendationSurface.HOME,
        )
        assertThat(result).hasSize(1)
    }

    private fun song(id: String, artist: String) = Song(
        id = id,
        title = id,
        artist = artist,
        artistId = artist.hashCode().toLong(),
        album = "Album",
        albumId = 1,
        path = "yt_$id",
        contentUriString = "yt://$id",
        albumArtUriString = null,
        duration = 180_000,
        mimeType = "audio/mp4",
        bitrate = 256,
        sampleRate = 44_100,
    )
}
