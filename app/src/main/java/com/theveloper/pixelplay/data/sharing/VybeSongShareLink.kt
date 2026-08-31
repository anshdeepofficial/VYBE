package com.theveloper.pixelplay.data.sharing

import android.net.Uri
import com.theveloper.pixelplay.data.model.Song

data class SharedVybeSong(
    val providerId: String?,
    // Maintain old fields for backwards compatibility with old links during transition
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: String? = null,
    val durationMs: Long = 0L,
    val albumBrowseId: String? = null,
)

object VybeSongShareLink {
    private const val VYBE_HOST = "vybetune.vercel.app"
    
    fun build(song: Song): Uri {
        val portableId = song.id.takeIf(::isPortableProviderId)
        if (portableId != null) {
            val builder = Uri.Builder()
                .scheme("https")
                .authority(VYBE_HOST)
            if (portableId.startsWith("yt_")) {
                builder.appendPath("watch")
                    .appendQueryParameter("v", portableId.removePrefix("yt_"))
            } else {
                builder.appendPath("play")
                    .appendQueryParameter("id", portableId)
            }
            builder.appendQueryParameter("title", song.title)
                .appendQueryParameter("artist", song.displayArtist)
                .appendQueryParameter("album", song.album)
                .appendQueryParameter("duration", song.duration.toString())
            song.albumArtUriString?.takeIf { it.startsWith("https://") }
                ?.let { builder.appendQueryParameter("art", it) }
            song.remoteAlbumBrowseId?.let { builder.appendQueryParameter("albumId", it) }
            return builder.build()
        }
        
        // Fallback for non-portable IDs
        return Uri.Builder()
            .scheme("vybe")
            .authority("play")
            .appendQueryParameter("title", song.title)
            .appendQueryParameter("artist", song.displayArtist)
            .build()
    }

    fun shareText(song: Song): String = buildString {
        append(song.title)
        if (song.displayArtist.isNotBlank()) append(" by ${song.displayArtist}")
        append("\n\nListen on VYBE:\n${build(song)}")
    }

    fun parse(uri: Uri): SharedVybeSong? {
        // Current website link plus legacy links already shared by older builds.
        val isWebLink = (uri.scheme == "http" || uri.scheme == "https") && (
            (uri.host == VYBE_HOST && (uri.path == "/play" || uri.path == "/watch")) ||
                (uri.host == "music.vybe.app" && uri.path?.contains("/watch") == true) ||
                (uri.host == "anshdeepofficial.github.io" && uri.path?.startsWith("/VYBE/play") == true)
            )
        if (isWebLink) {
            val portableId = uri.getQueryParameter("id")?.take(100)?.takeIf(::isPortableProviderId)
                ?: uri.getQueryParameter("v")?.take(100)?.let { "yt_$it" }
            if (portableId != null) {
                return SharedVybeSong(
                    providerId = portableId,
                    title = uri.getQueryParameter("title")?.trim()?.take(200).orEmpty(),
                    artist = uri.getQueryParameter("artist")?.trim()?.take(200).orEmpty(),
                    album = uri.getQueryParameter("album")?.trim()?.take(200).orEmpty(),
                    artwork = uri.getQueryParameter("art")?.take(2_000)?.takeIf { it.startsWith("https://") },
                    durationMs = uri.getQueryParameter("duration")?.toLongOrNull()?.coerceIn(0L, 86_400_000L) ?: 0L,
                    albumBrowseId = uri.getQueryParameter("albumId")?.take(200),
                )
            }
        }
        
        // Parse old vybe://play links
        if (uri.scheme == "vybe" && uri.host == "play") {
            return SharedVybeSong(
                providerId = uri.getQueryParameter("id")?.take(100)?.takeIf(::isPortableProviderId),
                title = uri.getQueryParameter("title")?.trim()?.take(200).orEmpty(),
                artist = uri.getQueryParameter("artist")?.trim()?.take(200).orEmpty(),
                album = uri.getQueryParameter("album")?.trim()?.take(200).orEmpty(),
                artwork = uri.getQueryParameter("art")?.take(2_000)?.takeIf { it.startsWith("https://") },
                durationMs = uri.getQueryParameter("duration")?.toLongOrNull()?.coerceIn(0L, 24L * 60L * 60L * 1_000L) ?: 0L,
                albumBrowseId = uri.getQueryParameter("albumId")?.take(200),
            )
        }
        return null
    }

    private fun isPortableProviderId(id: String): Boolean =
        id.startsWith("yt_") || id.startsWith("saavn_") || id.startsWith("audius_")
}
