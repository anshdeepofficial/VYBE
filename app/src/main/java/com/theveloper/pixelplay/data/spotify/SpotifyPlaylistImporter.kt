package com.theveloper.pixelplay.data.spotify

import com.theveloper.pixelplay.data.database.OnlineSongCacheDao
import com.theveloper.pixelplay.data.database.OnlineSongCacheEntity
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.repository.OnlineMusicRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SpotifyImportProgress(
    val playlistName: String,
    val processed: Int,
    val total: Int,
)

data class SpotifyImportResult(
    val playlistId: String,
    val playlistName: String,
    val importedCount: Int,
    val unavailableCount: Int,
    val unmatchedTracks: List<SpotifyUnmatchedTrack>,
)

data class SpotifyUnmatchedTrack(
    val key: String,
    val title: String,
    val artists: String,
    val album: String,
    val albumArtUrl: String?,
    val durationMs: Long,
)

data class SpotifyMatchCandidate(val song: Song)

private data class SpotifyTrack(
    val key: String,
    val title: String,
    val artists: String,
    val album: String,
    val albumArtUrl: String?,
    val durationMs: Long,
) {
    fun toUnmatched() = SpotifyUnmatchedTrack(key, title, artists, album, albumArtUrl, durationMs)
}

private data class SpotifyPlaylistPayload(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val tracks: List<SpotifyTrack>,
)

@Singleton
class SpotifyPlaylistImporter @Inject constructor(
    private val httpClient: OkHttpClient,
    private val onlineMusicRepository: OnlineMusicRepository,
    private val onlineSongCacheDao: OnlineSongCacheDao,
    private val playlistRepository: PlaylistPreferencesRepository,
    private val spotifyAccountRepository: SpotifyAccountRepository,
) {
    private data class CorrectionSession(
        val tracks: List<SpotifyTrack>,
        val matchedSongIds: MutableMap<String, String>,
    )

    private val correctionSessions = ConcurrentHashMap<String, CorrectionSession>()

    suspend fun importOnce(
        link: String,
        onProgress: (SpotifyImportProgress) -> Unit,
    ): SpotifyImportResult = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(link)
            ?: throw IllegalArgumentException("Please enter a valid public Spotify playlist link")
        val playlist = fetchPlaylist(playlistId)
        importPayload(playlist, onProgress)
    }

    suspend fun importAccountPlaylist(
        playlistId: String,
        onProgress: (SpotifyImportProgress) -> Unit,
    ): SpotifyImportResult = withContext(Dispatchers.IO) {
        val accountPlaylist = spotifyAccountRepository.getPlaylistForImport(playlistId)
        importPayload(
            SpotifyPlaylistPayload(
                id = accountPlaylist.id,
                name = accountPlaylist.name,
                coverUrl = accountPlaylist.coverUrl,
                tracks = accountPlaylist.tracks.map { track ->
                    SpotifyTrack(
                        key = track.key,
                        title = track.title,
                        artists = track.artists,
                        album = track.album,
                        albumArtUrl = track.albumArtUrl,
                        durationMs = track.durationMs,
                    )
                },
            ),
            onProgress,
        )
    }

    private suspend fun importPayload(
        playlist: SpotifyPlaylistPayload,
        onProgress: (SpotifyImportProgress) -> Unit,
    ): SpotifyImportResult {
        val completed = AtomicInteger(0)
        val limiter = Semaphore(4)

        val matched = coroutineScope {
            playlist.tracks.map { track ->
                async {
                    limiter.withPermit {
                        try {
                            val candidates = onlineMusicRepository.searchSongs(
                                query = "${track.title} ${track.artists}".trim()
                            )
                            pickBestMatch(track, candidates)?.let { song -> track to song }
                        } catch (_: Exception) {
                            null
                        } finally {
                            onProgress(
                                SpotifyImportProgress(
                                    playlistName = playlist.name,
                                    processed = completed.incrementAndGet(),
                                    total = playlist.tracks.size,
                                )
                            )
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val matchedByKey = matched.associate { (track, song) -> track.key to song.id }.toMutableMap()

        val cachedSongs = matched.map { (spotifyTrack, playableSong) ->
            OnlineSongCacheEntity(
                id = playableSong.id,
                title = spotifyTrack.title,
                artist = spotifyTrack.artists.ifBlank { playableSong.artist },
                album = spotifyTrack.album.ifBlank { playableSong.album },
                albumArtUrl = spotifyTrack.albumArtUrl ?: playableSong.albumArtUriString,
                duration = spotifyTrack.durationMs.takeIf { it > 0 } ?: playableSong.duration,
                path = playableSong.path,
                contentUri = playableSong.contentUriString,
                mimeType = playableSong.mimeType,
            )
        }
        if (cachedSongs.isNotEmpty()) onlineSongCacheDao.upsertAll(cachedSongs)

        val localPlaylistId = "spotify_${playlist.id}"
        playlistRepository.createPlaylist(
            name = playlist.name,
            songIds = cachedSongs.map { it.id }.distinct(),
            coverImageUri = playlist.coverUrl,
            customId = localPlaylistId,
            source = "SPOTIFY",
        )

        val unmatched = playlist.tracks.filter { it.key !in matchedByKey }.map { it.toUnmatched() }
        correctionSessions[localPlaylistId] = CorrectionSession(playlist.tracks, matchedByKey)

        return SpotifyImportResult(
            playlistId = localPlaylistId,
            playlistName = playlist.name,
            importedCount = cachedSongs.size,
            unavailableCount = unmatched.size,
            unmatchedTracks = unmatched,
        )
    }

    suspend fun findManualCandidates(
        track: SpotifyUnmatchedTrack,
        query: String,
    ): List<SpotifyMatchCandidate> = withContext(Dispatchers.IO) {
        val resolvedQuery = query.trim().ifBlank { "${track.title} ${track.artists}".trim() }
        onlineMusicRepository.searchSongs(resolvedQuery)
            .distinctBy { it.id }
            .take(12)
            .map(::SpotifyMatchCandidate)
    }

    suspend fun applyManualMatch(
        playlistId: String,
        track: SpotifyUnmatchedTrack,
        candidate: SpotifyMatchCandidate,
    ) = withContext(Dispatchers.IO) {
        val session = correctionSessions[playlistId]
            ?: error("The Spotify correction session expired. Import the playlist again.")
        val playable = candidate.song
        onlineSongCacheDao.upsertAll(
            listOf(
                OnlineSongCacheEntity(
                    id = playable.id,
                    title = track.title,
                    artist = track.artists.ifBlank { playable.artist },
                    album = track.album.ifBlank { playable.album },
                    albumArtUrl = track.albumArtUrl ?: playable.albumArtUriString,
                    duration = track.durationMs.takeIf { it > 0L } ?: playable.duration,
                    path = playable.path,
                    contentUri = playable.contentUriString,
                    mimeType = playable.mimeType,
                )
            )
        )
        session.matchedSongIds[track.key] = playable.id
        val orderedIds = session.tracks.mapNotNull { session.matchedSongIds[it.key] }.distinct()
        playlistRepository.reorderSongsInPlaylist(playlistId, orderedIds)
    }

    private fun extractPlaylistId(input: String): String? {
        val value = input.trim()
        SPOTIFY_URI.find(value)?.groupValues?.getOrNull(1)?.let { return it }
        return SPOTIFY_URL.find(value)?.groupValues?.getOrNull(1)
    }

    private fun fetchPlaylist(playlistId: String): SpotifyPlaylistPayload {
        val embedHtml = execute(
            Request.Builder()
                .url("https://open.spotify.com/embed/playlist/$playlistId")
                .header("User-Agent", USER_AGENT)
                .build()
        )
        val nextData = NEXT_DATA.find(embedHtml)?.groupValues?.getOrNull(1)
            ?: error("Spotify did not return playlist data. Make sure the playlist is public.")
        val state = findPlaylistState(JSONObject(nextData))
            ?: error("Spotify could not read this playlist. Confirm that the playlist is public and try its full Spotify URL again.")
        val entity = state.optJSONObject("data")?.optJSONObject("entity")
            ?: error("Spotify did not return public playlist details. Please try again.")
        val accessToken = state.optJSONObject("settings")
            ?.optJSONObject("session")
            ?.optString("accessToken")
            ?.takeIf(String::isNotBlank)
            ?: error("Spotify temporarily refused playlist access. Please try again.")
        val name = entity.optString("name").ifBlank { entity.optString("title", "Spotify Playlist") }
        val coverUrl = entity.optJSONObject("coverArt")
            ?.optJSONArray("sources")
            ?.optJSONObject(0)
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }

        val tracks = mutableListOf<SpotifyTrack>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total) {
            val page = fetchTrackPage(playlistId, accessToken, offset)
            val content = page.getJSONObject("data")
                .getJSONObject("playlistV2")
                .getJSONObject("content")
            total = content.optInt("totalCount", 0)
            val items = content.optJSONArray("items") ?: JSONArray()
            if (items.length() == 0) break
            for (index in 0 until items.length()) {
                parseTrack(items.optJSONObject(index), "$offset:$index")?.let(tracks::add)
            }
            offset += items.length()
        }
        if (tracks.isEmpty()) error("This public Spotify playlist has no importable songs")
        return SpotifyPlaylistPayload(playlistId, name, coverUrl, tracks)
    }

    private fun fetchTrackPage(playlistId: String, accessToken: String, offset: Int): JSONObject {
        val body = JSONObject().apply {
            put("operationName", "queryPlaylist")
            put("variables", JSONObject().apply {
                put("uri", "spotify:playlist:$playlistId")
                put("limit", PAGE_SIZE)
                put("offset", offset)
            })
            put("extensions", JSONObject().apply {
                put("persistedQuery", JSONObject().apply {
                    put("version", 1)
                    put("sha256Hash", PLAYLIST_QUERY_HASH)
                })
            })
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("https://api-partner.spotify.com/pathfinder/v1/query")
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .header("app-platform", "WebPlayer")
            .post(body)
            .build()
        return JSONObject(execute(request))
    }

    /**
     * Spotify occasionally moves the embed payload below a different pageProps key. Find the
     * object by its stable data/entity and settings/session shape instead of assuming one path.
     */
    private fun findPlaylistState(value: Any?, depth: Int = 0): JSONObject? {
        if (depth > MAX_STATE_SEARCH_DEPTH) return null
        return when (value) {
            is JSONObject -> {
                val hasEntity = value.optJSONObject("data")?.optJSONObject("entity") != null
                val hasToken = value.optJSONObject("settings")
                    ?.optJSONObject("session")
                    ?.optString("accessToken")
                    ?.isNotBlank() == true
                if (hasEntity && hasToken) {
                    value
                } else {
                    val keys = value.keys()
                    var match: JSONObject? = null
                    while (keys.hasNext() && match == null) {
                        match = findPlaylistState(value.opt(keys.next()), depth + 1)
                    }
                    match
                }
            }
            is JSONArray -> {
                var match: JSONObject? = null
                for (index in 0 until value.length()) {
                    match = findPlaylistState(value.opt(index), depth + 1)
                    if (match != null) break
                }
                match
            }
            else -> null
        }
    }

    private fun parseTrack(item: JSONObject?, positionKey: String): SpotifyTrack? {
        val data = item?.optJSONObject("itemV2")?.optJSONObject("data") ?: return null
        if (!data.optString("uri").startsWith("spotify:track:")) return null
        val title = data.optString("name").trim()
        if (title.isEmpty()) return null
        val artistItems = data.optJSONObject("artists")?.optJSONArray("items") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until artistItems.length()) {
                artistItems.optJSONObject(index)
                    ?.optJSONObject("profile")
                    ?.optString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }.joinToString(", ")
        val album = data.optJSONObject("albumOfTrack")
        val art = album?.optJSONObject("coverArt")
            ?.optJSONArray("sources")
            ?.optJSONObject(0)
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }
        return SpotifyTrack(
            key = "${data.optString("uri")}:$positionKey",
            title = title,
            artists = artists,
            album = album?.optString("name").orEmpty(),
            albumArtUrl = art,
            durationMs = data.optJSONObject("duration")?.optLong("totalMilliseconds") ?: 0L,
        )
    }

    private fun pickBestMatch(track: SpotifyTrack, candidates: List<Song>): Song? {
        if (candidates.isEmpty()) return null
        val wantedTitle = normalize(track.title)
        val wantedArtists = normalize(track.artists)
        val wantedAlbum = normalize(track.album)
        val scored = candidates.map { candidate ->
            val title = normalize(candidate.title)
            val artist = normalize(candidate.displayArtist)
            val album = normalize(candidate.album)
            val titleScore = when {
                title == wantedTitle -> 80
                title.contains(wantedTitle) || wantedTitle.contains(title) -> 58
                tokenSimilarity(title, wantedTitle) >= 0.7 -> 50
                else -> 0
            }
            val artistScore = when {
                wantedArtists.isBlank() -> 0
                artist == wantedArtists -> 35
                artist.contains(wantedArtists) || wantedArtists.contains(artist) -> 28
                tokenSimilarity(artist, wantedArtists) >= 0.5 -> 20
                else -> 0
            }
            val albumScore = if (wantedAlbum.isNotBlank() && album == wantedAlbum) 8 else 0
            val durationDifference = if (track.durationMs > 0L && candidate.duration > 0L) {
                kotlin.math.abs(track.durationMs - candidate.duration)
            } else null
            val durationScore = when {
                durationDifference == null -> 0
                durationDifference <= 4_000L -> 14
                durationDifference <= 10_000L -> 6
                durationDifference > 30_000L -> -30
                else -> 0
            }
            candidate to (titleScore + artistScore + albumScore + durationScore)
        }
        return scored.maxByOrNull { it.second }
            ?.takeIf { it.second >= MIN_MATCH_SCORE }
            ?.first
    }

    private fun tokenSimilarity(first: String, second: String): Double {
        val left = first.chunked(2).toSet()
        val right = second.chunked(2).toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / left.union(right).size
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(NON_ALPHANUMERIC, "")

    private fun execute(request: Request): String = httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) error("Spotify request failed (${response.code})")
        body
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_STATE_SEARCH_DEPTH = 12
        const val MIN_MATCH_SCORE = 58
        const val PLAYLIST_QUERY_HASH = "908a5597b4d0af0489a9ad6a2d41bc3b416ff47c0884016d92bbd6822d0eb6d8"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val NEXT_DATA = Regex(
            """<script id=[\"']__NEXT_DATA__[\"'] type=[\"']application/json[\"']>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val SPOTIFY_URI = Regex("""spotify:playlist:([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
        val SPOTIFY_URL = Regex(
            """(?:open\.)?spotify\.com/(?:intl-[^/]+/)?(?:user/[^/]+/)?playlist/([A-Za-z0-9]+)""",
            RegexOption.IGNORE_CASE,
        )
        val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]""")
    }
}
