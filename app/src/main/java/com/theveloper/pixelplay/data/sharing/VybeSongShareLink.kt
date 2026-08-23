package com.theveloper.pixelplay.data.sharing

import android.net.Uri
import com.theveloper.pixelplay.data.model.Song

data class SharedVybeSong(
    val providerId: String?,
    val title: String,
    val artist: String,
    val album: String,
    val artwork: String?,
    val durationMs: Long,
    val albumBrowseId: String?,
)

object VybeSongShareLink {
    const val DOWNLOAD_URL = "https://github.com/anshdeepofficial/VYBE/releases/latest"

    fun build(song: Song): Uri = Uri.Builder()
        .scheme("vybe")
        .authority("play")
        .appendQueryParameter("id", song.id.takeIf(::isPortableProviderId))
        .appendQueryParameter("title", song.title)
        .appendQueryParameter("artist", song.displayArtist)
        .appendQueryParameter("album", song.album)
        .appendQueryParameter("art", song.albumArtUriString?.takeIf { it.startsWith("https://") })
        .appendQueryParameter("duration", song.duration.takeIf { it > 0L }?.toString())
        .appendQueryParameter("albumId", song.remoteAlbumBrowseId)
        .build()

    fun shareText(song: Song): String = buildString {
        append(song.title)
        if (song.displayArtist.isNotBlank()) append(" by ${song.displayArtist}")
        append("\n\nOpen in VYBE: ${build(song)}")
        append("\nGet VYBE: $DOWNLOAD_URL")
    }

    fun parse(uri: Uri): SharedVybeSong? {
        if (uri.scheme != "vybe" || uri.host != "play") return null
        val title = uri.getQueryParameter("title")?.trim()?.take(200).orEmpty()
        val artist = uri.getQueryParameter("artist")?.trim()?.take(200).orEmpty()
        if (title.isBlank()) return null
        return SharedVybeSong(
            providerId = uri.getQueryParameter("id")?.take(100)?.takeIf(::isPortableProviderId),
            title = title,
            artist = artist,
            album = uri.getQueryParameter("album")?.trim()?.take(200).orEmpty(),
            artwork = uri.getQueryParameter("art")?.take(2_000)?.takeIf { it.startsWith("https://") },
            durationMs = uri.getQueryParameter("duration")?.toLongOrNull()?.coerceIn(0L, 24L * 60L * 60L * 1_000L) ?: 0L,
            albumBrowseId = uri.getQueryParameter("albumId")?.take(200),
        )
    }

    private fun isPortableProviderId(id: String): Boolean =
        id.startsWith("yt_") || id.startsWith("saavn_") || id.startsWith("audius_")
}
