package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import java.text.Normalizer

class RecognitionTrackResolver(private val repository: OnlineMusicRepository) {
    suspend fun resolve(metadata: RecognitionMetadata): Song? {
        val query = listOfNotNull(metadata.isrc, metadata.title, metadata.artist).joinToString(" ")
        val candidates = repository.searchSongs(query).ifEmpty {
            repository.searchSongs("${metadata.title} ${metadata.artist}")
        }
        return candidates.maxByOrNull { score(it, metadata) }?.takeIf { score(it, metadata) >= 55 }
    }

    private fun score(song: Song, target: RecognitionMetadata): Int {
        val title = normalized(song.title)
        val artist = normalized(song.displayArtist)
        val targetTitle = normalized(target.title)
        val targetArtist = normalized(target.artist)
        var score = 0
        score += when {
            title == targetTitle -> 70
            title.contains(targetTitle) || targetTitle.contains(title) -> 35
            else -> 0
        }
        val artistScore = when {
            artist == targetArtist -> 40
            artist.contains(targetArtist) || targetArtist.contains(artist) -> 22
            else -> 0
        }
        if (artistScore == 0) return -100
        score += artistScore
        val recognizedVariant = variantWords(targetTitle)
        val candidateVariant = variantWords(title)
        if (candidateVariant.isNotEmpty() && candidateVariant != recognizedVariant) score -= 45
        if (target.album != null && normalized(song.album) == normalized(target.album)) score += 12
        return score
    }

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun variantWords(value: String): Set<String> = setOf("live", "remix", "karaoke", "cover", "slowed", "sped up", "acoustic")
        .filterTo(linkedSetOf()) { value.contains(it) }
}
