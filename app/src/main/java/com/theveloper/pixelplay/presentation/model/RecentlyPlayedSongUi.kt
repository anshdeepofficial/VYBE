package com.theveloper.pixelplay.presentation.model

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@Parcelize
data class RecentlyPlayedSongUiModel(
    val song: Song,
    val lastPlayedTimestamp: Long
) : Parcelable

fun mapRecentlyPlayedSongs(
    playbackHistory: List<PlaybackStatsRepository.PlaybackHistoryEntry>,
    songs: List<Song>,
    range: StatsTimeRange? = null,
    nowMillis: Long = System.currentTimeMillis(),
    maxItems: Int = Int.MAX_VALUE
): List<RecentlyPlayedSongUiModel> {
    if (maxItems <= 0 || playbackHistory.isEmpty()) return emptyList()

    val songById = songs.associateBy { it.id }
    val recentSongIds = collectRecentlyPlayedSongIds(
        playbackHistory = playbackHistory,
        range = range,
        nowMillis = nowMillis,
        maxItems = maxItems
    )

    if (recentSongIds.isEmpty()) return emptyList()

    val recentSongIdSet = recentSongIds.toHashSet()
    val (startBound, endBound) = range.resolveBounds(
        nowMillis = nowMillis.coerceAtLeast(0L),
        zoneId = ZoneId.systemDefault()
    )

    val seenSongIds = HashSet<String>()
    val deduped = ArrayList<RecentlyPlayedSongUiModel>(maxItems.coerceAtMost(playbackHistory.size))

    val sortedHistory = playbackHistory.sortedWith(
        compareByDescending<PlaybackStatsRepository.PlaybackHistoryEntry> { it.timestamp }
            .thenBy { it.songId }
    )

    for (entry in sortedHistory) {
        if (deduped.size >= maxItems) break
        val safeTimestamp = entry.timestamp.coerceAtLeast(0L)
        if (safeTimestamp > endBound) continue
        if (startBound != null && safeTimestamp < startBound) continue
        if (!seenSongIds.add(entry.songId)) continue
        if (entry.songId !in recentSongIdSet) continue

        val repoSong = songById[entry.songId]
        val song = if (repoSong != null) {
            if (repoSong.title == "Online Track" && repoSong.artist == "YouTube Music" && entry.track != null) {
                entry.track.toSong(entry.songId).copy(
                    path = repoSong.path,
                    contentUriString = repoSong.contentUriString
                )
            } else if (repoSong.title == "Online Track") {
                // If the local DB only has the placeholder and we don't have the history metadata, skip it
                null
            } else {
                repoSong.copy(
                    title = repoSong.title.ifBlank { entry.track?.title ?: "" },
                    artist = repoSong.artist.ifBlank { entry.track?.artist ?: "" },
                    albumArtUriString = repoSong.albumArtUriString ?: entry.track?.artworkUri
                )
            }
        } else {
            entry.track?.toSong(entry.songId)
        } ?: continue
        deduped += RecentlyPlayedSongUiModel(
            song = song,
            lastPlayedTimestamp = safeTimestamp
        )
    }

    return deduped
}

fun collectRecentlyPlayedSongIds(
    playbackHistory: List<PlaybackStatsRepository.PlaybackHistoryEntry>,
    range: StatsTimeRange? = null,
    nowMillis: Long = System.currentTimeMillis(),
    maxItems: Int = Int.MAX_VALUE
): List<String> {
    if (maxItems <= 0 || playbackHistory.isEmpty()) return emptyList()

    val (startBound, endBound) = range.resolveBounds(
        nowMillis = nowMillis.coerceAtLeast(0L),
        zoneId = ZoneId.systemDefault()
    )

    val seenSongIds = LinkedHashSet<String>()
    val sortedHistory = playbackHistory.sortedWith(
        compareByDescending<PlaybackStatsRepository.PlaybackHistoryEntry> { it.timestamp }
            .thenBy { it.songId }
    )

    for (entry in sortedHistory) {
        if (seenSongIds.size >= maxItems) break
        val safeTimestamp = entry.timestamp.coerceAtLeast(0L)
        if (safeTimestamp > endBound) continue
        if (startBound != null && safeTimestamp < startBound) continue
        seenSongIds.add(entry.songId)
    }

    return seenSongIds.toList()
}

private fun StatsTimeRange?.resolveBounds(
    nowMillis: Long,
    zoneId: ZoneId
): Pair<Long?, Long> {
    val safeNow = nowMillis.coerceAtLeast(0L)
    val zonedNow = java.time.Instant.ofEpochMilli(safeNow).atZone(zoneId)

    return when (this) {
        StatsTimeRange.DAY -> {
            val start = zonedNow.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
            start to safeNow
        }
        StatsTimeRange.WEEK -> {
            val startOfWeek = zonedNow.toLocalDate().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val start = startOfWeek.atStartOfDay(zoneId).toInstant().toEpochMilli()
            start to safeNow
        }
        StatsTimeRange.MONTH -> {
            val startOfMonth = zonedNow.toLocalDate().withDayOfMonth(1)
            val start = startOfMonth.atStartOfDay(zoneId).toInstant().toEpochMilli()
            start to safeNow
        }
        StatsTimeRange.YEAR -> {
            val startOfYear = zonedNow.toLocalDate().withDayOfYear(1)
            val start = startOfYear.atStartOfDay(zoneId).toInstant().toEpochMilli()
            start to safeNow
        }
        StatsTimeRange.ALL, null -> {
            null to safeNow
        }
    }
}
