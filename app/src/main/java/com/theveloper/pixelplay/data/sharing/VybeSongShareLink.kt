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
    private const val VYBE_DOMAIN = "vybe.app"
    
    fun build(song: Song): Uri {
        val portableId = song.id.takeIf(::isPortableProviderId)
        if (portableId != null) {
            val cleanId = portableId.removePrefix("yt_")
            return Uri.Builder()
                .scheme("https")
                .authority("music.$VYBE_DOMAIN")
                .appendPath("watch")
                .appendQueryParameter("v", cleanId)
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
        append("\n\nListen on VYBE:\n${build(song)}")
    }

    fun parse(uri: Uri): SharedVybeSong? {
        // Parse new https://music.vybe.app/watch?v=ID links
        if ((uri.scheme == "http" || uri.scheme == "https") && uri.host == "music.$VYBE_DOMAIN" && uri.path?.contains("/watch") == true) {
            val id = uri.getQueryParameter("v")?.take(100)
            if (id != null) {
                return SharedVybeSong(providerId = "yt_$id")
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
