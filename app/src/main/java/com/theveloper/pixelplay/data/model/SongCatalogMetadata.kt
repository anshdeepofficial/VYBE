package com.theveloper.pixelplay.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stable catalogue metadata shared by queue, Media3 and database persistence.
 * Transport URLs deliberately do not belong here.
 */
@Serializable
data class SongCatalogMetadata(
    val songId: String,
    val artists: List<CatalogArtist> = emptyList(),
    val albumBrowseId: String? = null,
    val albumArtist: String? = null,
    val releaseDateEpochMillis: Long = 0L,
    val isMusicVideo: Boolean = false,
    val year: Int = 0,
    val trackNumber: Int = 0,
    val discNumber: Int? = null,
) {
    /** Never apply another recording's metadata to an item, even from old caches. */
    fun applyTo(song: Song): Song = if (song.id != songId) song else song.copy(
        artists = artists.map { ArtistRef(it.id, it.name, it.isPrimary, it.browseId) },
        remoteAlbumBrowseId = albumBrowseId,
        albumArtist = albumArtist,
        releaseDateEpochMillis = releaseDateEpochMillis,
        isMusicVideo = isMusicVideo,
        year = year,
        trackNumber = trackNumber,
        discNumber = discNumber,
    )

    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(value: String?): SongCatalogMetadata? {
            if (value.isNullOrBlank()) return null
            return try {
                json.decodeFromString<SongCatalogMetadata>(value)
            } catch (_: IllegalArgumentException) {
                // Old or malformed optional metadata must not make a saved queue unreadable.
                null
            }
        }
    }
}

@Serializable
data class CatalogArtist(
    val id: Long,
    val name: String,
    val isPrimary: Boolean = false,
    val browseId: String? = null,
)

fun Song.catalogMetadata(): SongCatalogMetadata = SongCatalogMetadata(
    songId = id,
    artists = artists.map { CatalogArtist(it.id, it.name, it.isPrimary, it.remoteBrowseId) },
    albumBrowseId = remoteAlbumBrowseId,
    albumArtist = albumArtist,
    releaseDateEpochMillis = releaseDateEpochMillis,
    isMusicVideo = isMusicVideo,
    year = year,
    trackNumber = trackNumber,
    discNumber = discNumber,
)
