package com.theveloper.pixelplay.data.network.ytmusic

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.SongMetadataCleaner
import kotlinx.parcelize.Parcelize

enum class YouTubeMusicEntityType { SONG, MUSIC_VIDEO }

@Immutable
@Parcelize
data class YouTubeTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "YouTube Music",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    /** Playlist-scoped identifier required by YouTube Music for removal/reordering. */
    val setVideoId: String? = null,
    val resultType: YouTubeMusicEntityType = YouTubeMusicEntityType.SONG,
    val isOfficial: Boolean = false,
    val linkedArtists: List<YouTubeArtist> = emptyList(),
    val albumBrowseId: String? = null,
) : Parcelable {
    fun toSong(streamUrl: String? = null): Song {
        val cleaned = SongMetadataCleaner.clean(title, artist)
        val finalStream = streamUrl ?: "yt_$videoId"
        return Song(
            id = "yt_$videoId",
            title = cleaned.title,
            artist = cleaned.artist,
            artistId = 0L,
            artists = linkedArtists.mapIndexed { index, linked ->
                ArtistRef(
                    id = linked.browseId.hashCode().toLong(),
                    name = linked.name,
                    isPrimary = index == 0,
                    remoteBrowseId = linked.browseId,
                )
            },
            album = album.ifBlank { "YouTube Music" },
            albumId = 0L,
            path = finalStream,
            contentUriString = "yt://$videoId",
            albumArtUriString = thumbnailUrl,
            duration = if (durationSeconds > 0) durationSeconds * 1000L else 0L,
            mimeType = "audio/mp4",
            bitrate = 256,
            sampleRate = 44100,
            remoteAlbumBrowseId = albumBrowseId,
        )
    }
}

@Immutable
@Parcelize
data class YouTubeAlbum(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val trackCount: Int = 0,
    val type: String = "Album", // Album, Single, EP
    val thumbnailUrl: String? = null
) : Parcelable {
    fun toAlbum(): Album {
        return Album(
            id = browseId.hashCode().toLong(),
            title = title,
            artist = artist,
            year = year ?: 0,
            dateAdded = System.currentTimeMillis(),
            albumArtUriString = thumbnailUrl,
            songCount = trackCount,
            albumArtist = artist,
            remoteBrowseId = browseId,
        )
    }
}

@Immutable
@Parcelize
data class YouTubeArtist(
    val browseId: String,
    val name: String,
    val subscriberCount: String? = null,
    val thumbnailUrl: String? = null
) : Parcelable {
    fun toArtist(): Artist {
        return Artist(
            id = browseId.hashCode().toLong(),
            name = name,
            songCount = 0,
            imageUrl = thumbnailUrl,
            customImageUri = null,
            remoteBrowseId = browseId,
        )
    }
}

@Immutable
data class YouTubeSearchResult(
    val songs: List<Song> = emptyList(),
    val albums: List<YouTubeAlbum> = emptyList(),
    val artists: List<YouTubeArtist> = emptyList()
)

@Immutable
data class YouTubeArtistProfile(
    val browseId: String,
    val name: String,
    val bannerUrl: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val subscribers: String? = null,
    val topSongs: List<Song> = emptyList(),
    val videos: List<Song> = emptyList(),
    val albums: List<YouTubeAlbum> = emptyList(),
    val singles: List<YouTubeAlbum> = emptyList(),
    val relatedArtists: List<YouTubeArtist> = emptyList()
)

@Immutable
data class YouTubeAlbumDetails(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val trackCount: Int = 0,
    val coverUrl: String? = null,
    val albumType: String = "Album",
    val description: String? = null,
    val tracks: List<Song> = emptyList()
)
