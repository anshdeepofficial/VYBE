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
    const val DOWNLOAD_URL = "https://github.com/anshdeepofficial/VYBE/releases/latest"
    const val HTTPS_HOST = "anshdeepofficial.github.io"
    const val HTTPS_PATH_PREFIX = "/VYBE/play/"

    fun build(song: Song): Uri {
        val portableId = song.id.takeIf(::isPortableProviderId)
        if (portableId != null) {
            return Uri.Builder()
                .scheme("https")
                .authority(HTTPS_HOST)
                .path("$HTTPS_PATH_PREFIX$portableId")
                .build()
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
        append("\n\nOpen in VYBE: ${build(song)}")
        append("\nGet VYBE: $DOWNLOAD_URL")
    }

    fun parse(uri: Uri): SharedVybeSong? {
        // Parse new HTTPS links: https://anshdeepofficial.github.io/VYBE/play/<TRACK_ID>
        if (uri.scheme == "https" && uri.host == HTTPS_HOST) {
            val path = uri.path
            if (path != null && path.startsWith(HTTPS_PATH_PREFIX)) {
                val trackId = path.substring(HTTPS_PATH_PREFIX.length).take(100)
                if (isPortableProviderId(trackId)) {
                    return SharedVybeSong(
                        providerId = trackId,
                    )
                }
            }
        }
        
        // Parse old legacy vybe://play links
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
