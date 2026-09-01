package com.theveloper.pixelplay.data.recommendation

import com.theveloper.pixelplay.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

enum class RecommendationSurface { HOME, SEARCH, LIBRARY, AUTOPLAY }

data class RecommendationProfile(
    val artistAffinity: Map<String, Int> = emptyMap(),
    val genreAffinity: Map<String, Int> = emptyMap(),
    val preferredArtists: Set<String> = emptySet(),
    val preferredGenres: Set<String> = emptySet(),
    val blockedArtists: Set<String> = emptySet(),
) {
    companion object {
        fun fromTaste(
            songs: List<Song>,
            preferredArtists: Set<String> = emptySet(),
            preferredGenres: Set<String> = emptySet(),
            blockedArtists: Set<String> = emptySet(),
        ) = RecommendationProfile(
            artistAffinity = songs.flatMap { song ->
                song.artists.map { it.name }.ifEmpty { listOf(song.displayArtist) }
            }.map(::normalize).filter(String::isNotBlank).groupingBy { it }.eachCount(),
            genreAffinity = songs.mapNotNull { it.genre?.let(::normalize)?.takeIf(String::isNotBlank) }
                .groupingBy { it }.eachCount(),
            preferredArtists = preferredArtists.mapTo(mutableSetOf(), ::normalize),
            preferredGenres = preferredGenres.mapTo(mutableSetOf(), ::normalize),
            blockedArtists = blockedArtists.mapTo(mutableSetOf(), ::normalize),
        )
    }
}

/** One deterministic taste ranker shared by Home, Search, Library and autoplay. */
@Singleton
class UnifiedRecommendationEngine @Inject constructor() {
    fun rank(
        candidates: List<Song>,
        profile: RecommendationProfile,
        surface: RecommendationSurface,
        limit: Int = candidates.size,
        query: String? = null,
    ): List<Song> {
        val queryKey = normalize(query.orEmpty())
        val providerOrder = candidates.withIndex().associate { it.value.id to it.index }
        val scored = candidates
            .filter { it.title.isNotBlank() && it.displayArtist.isNotBlank() }
            .filterNot { song -> song.artistKeys().any(profile.blockedArtists::contains) }
            .distinctBy { song -> song.identityKey() }
            .map { song ->
                val artists = song.artistKeys()
                val genre = normalize(song.genre.orEmpty())
                val metadata = normalize("${song.title} ${song.displayArtist} ${song.album} ${song.genre.orEmpty()}")
                val affinity = artists.maxOfOrNull { profile.artistAffinity[it] ?: 0 } ?: 0
                val preferredArtist = artists.any(profile.preferredArtists::contains)
                val genreAffinity = profile.genreAffinity[genre] ?: 0
                val preferredGenre = genre.isNotBlank() && genre in profile.preferredGenres
                val queryScore = when {
                    queryKey.isBlank() -> 0
                    normalize(song.title) == queryKey -> 120
                    normalize(song.title).startsWith(queryKey) -> 70
                    metadata.contains(queryKey) -> 35
                    else -> 0
                }
                val surfaceBoost = when (surface) {
                    RecommendationSurface.HOME -> affinity * 9 + genreAffinity * 4
                    RecommendationSurface.SEARCH -> affinity * 5 + genreAffinity * 2 + queryScore
                    RecommendationSurface.LIBRARY -> affinity * 12 + genreAffinity * 3
                    RecommendationSurface.AUTOPLAY -> affinity * 10 + genreAffinity * 6
                }
                song to (
                    surfaceBoost +
                        (if (preferredArtist) 35 else 0) +
                        (if (preferredGenre) 18 else 0)
                )
            }
            .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }
                .thenBy { providerOrder[it.first.id] ?: Int.MAX_VALUE })

        // Keep shelves varied without allowing unrelated filler to outrank real taste signals.
        val artistCounts = mutableMapOf<String, Int>()
        val result = ArrayList<Song>(limit.coerceAtMost(scored.size))
        scored.forEach { (song, _) ->
            val primary = song.artistKeys().firstOrNull().orEmpty()
            val cap = if (surface == RecommendationSurface.SEARCH && queryKey.isNotBlank()) 4 else 2
            if ((artistCounts[primary] ?: 0) < cap || result.size < 3) {
                result += song
                artistCounts[primary] = (artistCounts[primary] ?: 0) + 1
            }
            if (result.size >= limit) return@forEach
        }
        return result.take(limit)
    }
}

private fun Song.artistKeys(): List<String> =
    artists.map { normalize(it.name) }.ifEmpty { listOf(normalize(displayArtist)) }.filter(String::isNotBlank)

private fun Song.identityKey(): String = id.takeIf(String::isNotBlank)
    ?: "${normalize(title)}|${normalize(displayArtist)}|${duration / 2_000L}"

private fun normalize(value: String): String = value.trim().lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
