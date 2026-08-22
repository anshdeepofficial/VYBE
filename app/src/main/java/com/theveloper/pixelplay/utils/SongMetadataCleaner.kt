package com.theveloper.pixelplay.utils

/**
 * Normalizes online / file track titles and artists so that:
 * 1. The Song Title is pure, clean, and never prefixed with the artist name.
 * 2. The Artist Name is cleanly separated from the title.
 * 3. Video/Audio tags like "(Official Video)", "[HQ]", "(Lyrics)" are stripped.
 */
object SongMetadataCleaner {

    private val VIDEO_AUDIO_TAGS_REGEX = Regex(
        "(?i)\\s*[\\[\\(]\\s*(official\\s+(audio|video|music\\s+video|visualizer|lyric\\s+video|lyrics|hd|4k|remastered)|hq|audio|video|lyrics|extended\\s+mix|clean|explicit|full\\s+version)\\s*[\\]\\)]"
    )

    data class CleanedMetadata(
        val title: String,
        val artist: String
    )

    fun clean(rawTitle: String, rawArtist: String): CleanedMetadata {
        var title = rawTitle.trim()
        var artist = rawArtist.trim()

        // 1. Strip common video/audio suffixes from title: "(Official Music Video)", "[HQ]", etc.
        title = title.replace(VIDEO_AUDIO_TAGS_REGEX, "").trim()

        // 2. Normalize separator patterns: " - ", " – ", " — ", " | ", " // ", " : "
        val separators = listOf(" - ", " – ", " — ", " | ", " // ")
        for (sep in separators) {
            if (title.contains(sep)) {
                val parts = title.split(sep, limit = 2).map { it.trim() }
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    val part0 = parts[0]
                    val part1 = parts[1]

                    // Case A: Title is "Artist - SongTitle"
                    if (artist.isNotBlank() && (part0.equals(artist, ignoreCase = true) || part0.contains(artist, ignoreCase = true))) {
                        title = part1
                        // Artist remains as is
                        break
                    }
                    // Case B: Title is "SongTitle - Artist"
                    else if (artist.isNotBlank() && (part1.equals(artist, ignoreCase = true) || part1.contains(artist, ignoreCase = true))) {
                        title = part0
                        break
                    }
                    // Case C: Artist is generic ("Unknown", "Audius", "Channel") -> part0 is artist, part1 is title
                    else if (artist.isBlank() || artist.equals("Unknown Artist", ignoreCase = true) || artist.equals("Audius", ignoreCase = true)) {
                        artist = part0
                        title = part1
                        break
                    }
                    // Case D: Title has "Artist - SongName" with no strong artist matching
                    else {
                        // In standard music uploads, "Artist - Song Title" is the dominant convention
                        // If part0 looks like an artist and part1 is the track title
                        title = part1
                        if (artist.isBlank() || artist.equals("Unknown Artist", ignoreCase = true)) {
                            artist = part0
                        }
                        break
                    }
                }
            }
        }

        // Secondary check for "Title (feat. Artist)" or "Title ft. Artist"
        val featRegex = Regex("(?i)\\s*[\\[\\(]?(feat\\.?|ft\\.?)\\s+([^\\]\\)]+)[\\]\\)]?")
        val featMatch = featRegex.find(title)
        if (featMatch != null) {
            val featuredArtist = featMatch.groupValues[2].trim()
            title = title.replace(featMatch.value, "").trim()
            if (artist.isNotBlank() && !artist.contains(featuredArtist, ignoreCase = true)) {
                artist = "$artist feat. $featuredArtist"
            }
        }

        // Final cleanup
        title = title.trim().trim('-', '–', '—', '|', ':').trim()
        if (title.isBlank()) {
            title = rawTitle.ifBlank { "Unknown Title" }
        }
        if (artist.isBlank()) {
            artist = rawArtist.ifBlank { "Unknown Artist" }
        }

        return CleanedMetadata(title = title, artist = artist)
    }
}
