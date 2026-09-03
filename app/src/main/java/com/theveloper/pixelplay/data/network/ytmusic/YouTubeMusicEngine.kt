package com.theveloper.pixelplay.data.network.ytmusic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val newPipeStreamResolver: NewPipeStreamResolver
) {
    private data class StructuredSearchBuckets(
        val songs: List<Song>,
        val albums: List<YouTubeAlbum>,
        val artists: List<YouTubeArtist>,
        val videos: List<Song>,
    )

    companion object {
        private const val TAG = "YouTubeMusicEngine"
        private const val INNERTUBE_MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
        private const val INNERTUBE_MAIN_BASE = "https://www.youtube.com/youtubei/v1"
        private const val WEB_REMIX_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val FALLBACK_WEB_REMIX_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val FALLBACK_WEB_REMIX_CLIENT_VERSION = "1.20260818.08.00"
        private const val WEB_REMIX_CLIENT_NAME_HEADER = "67"
        private val WEB_CONFIG_TTL_MS = TimeUnit.HOURS.toMillis(6)
        private val API_KEY_REGEX = Regex("""\"INNERTUBE_API_KEY\":\"([^\"]+)\"""")
        private val CLIENT_VERSION_REGEX = Regex("""\"INNERTUBE_(?:CONTEXT_)?CLIENT_VERSION\":\"([^\"]+)\"""")
        private val VISITOR_DATA_REGEX = Regex("""\"VISITOR_DATA\":\"([^\"]+)\"""")
        private val DURATION_TEXT_REGEX = Regex("""^(?:\d{1,2}:)?\d{1,2}:\d{2}$""")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net",
            "https://pipedapi.tokhmi.xyz",
            "https://piped-api.lunar.icu"
        )

        private val INVIDIOUS_INSTANCES = listOf(
            "https://invidious.nerdvpn.de",
            "https://invidious.drgns.space",
            "https://yt.artemislena.eu"
        )

        // Filter out non-music video titles
        private val NON_MUSIC_KEYWORDS = listOf(
            "tutorial", "interview", "podcast", "keynote", "vlog", "gameplay",
            "reaction", "review", "news", "how to", "unboxing", "full episode",
            "episode", "episodes", "audiobook", "audio book", "highlights",
            "press conference", "analysis", "talk show"
        )
        private val RESULT_TYPE_LABELS = setOf(
            "song", "video", "music video", "single", "album", "ep"
        )
        private const val SEARCH_FILTER_ALBUMS = "EgWKAQIYAWoSEAMQBBAKEAUQCRAOEBAQFRAR"
        private const val SEARCH_FILTER_ARTISTS = "EgWKAQIgAWoSEAMQBBAKEAUQCRAOEBAQFRAR"
        private const val SEARCH_FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        private const val SEARCH_FILTER_VIDEOS = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"
    }

    private val streamUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(4)

    @Volatile
    private var webRemixConfig: WebRemixConfig? = null

    @Volatile
    private var dataSaverEnabled: Boolean = false

    fun setDataSaverEnabled(enabled: Boolean) {
        if (dataSaverEnabled == enabled) return
        dataSaverEnabled = enabled
        streamUrlCache.clear()
    }

    /**
     * Explicit Data Saver always wins. Otherwise VYBE automatically selects an efficient
     * stream when Android reports a constrained/slow link, so a user does not need to find
     * the setting before playback becomes usable.
     */
    private fun preferEfficientStream(): Boolean {
        if (dataSaverEnabled) return true
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return true
        val capabilities = manager.getNetworkCapabilities(network) ?: return true
        val downstreamKbps = capabilities.linkDownstreamBandwidthKbps
        return downstreamKbps in 1 until 3_000 ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
    }

    // Persistent visitor data token — YouTube uses this for session continuity.
    // InnerTune/OuterTune/Meld all send this in payload + header.
    @Volatile
    private var visitorData: String = generateVisitorData()

    private fun generateVisitorData(): String {
        // Generate a base64-like visitor token similar to YouTube's format
        val random = java.util.UUID.randomUUID().toString().replace("-", "")
        return "Cgt${random.take(22)}%3D%3D"
    }

    /**
     * Invalidate cached stream URL for a video (e.g. on HTTP 403 or stream expiry).
     */
    fun invalidateStreamUrl(videoId: String) {
        val cleanId = videoId.removePrefix("yt_")
        streamUrlCache.remove(cleanId)
    }

    /**
     * Perform music-only search and categorize results into:
     * 1. Songs
     * 2. Albums
     * 3. Artists
     */
    suspend fun searchMusicStructured(query: String, region: String = "IN"): YouTubeSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext YouTubeSearchResult()

        val songs = mutableListOf<Song>()
        val albums = mutableListOf<YouTubeAlbum>()
        val artists = mutableListOf<YouTubeArtist>()
        val videos = mutableListOf<Song>()

        // Fetch exact song results and the mixed summary concurrently. The filtered
        // endpoint prevents similarly named uploads from outranking the real song,
        // while parallel execution keeps the search latency bounded.
        coroutineScope {
            val exactSongs = async(Dispatchers.IO) {
                mutableListOf<Song>().also { filtered ->
                    searchStructuredEndpoint(
                        cleanQuery,
                        region,
                        filtered,
                        mutableListOf(),
                        mutableListOf(),
                        mutableListOf(),
                        SEARCH_FILTER_SONGS,
                    )
                }
            }
            val exactVideos = async(Dispatchers.IO) {
                mutableListOf<Song>().also { filtered ->
                    searchStructuredEndpoint(
                        cleanQuery, region, mutableListOf(), mutableListOf(),
                        mutableListOf(), filtered, SEARCH_FILTER_VIDEOS,
                    )
                }
            }
            val mixedResults = async(Dispatchers.IO) {
                val mixedSongs = mutableListOf<Song>()
                val mixedAlbums = mutableListOf<YouTubeAlbum>()
                val mixedArtists = mutableListOf<YouTubeArtist>()
                val mixedVideos = mutableListOf<Song>()
                searchStructuredEndpoint(cleanQuery, region, mixedSongs, mixedAlbums, mixedArtists, mixedVideos)
                StructuredSearchBuckets(mixedSongs, mixedAlbums, mixedArtists, mixedVideos)
            }
            songs += exactSongs.await()
            videos += exactVideos.await().map { it.copy(isMusicVideo = true) }
            val mixed = mixedResults.await()
            songs += mixed.songs
            albums += mixed.albums
            artists += mixed.artists
            videos += mixed.videos
        }

        // Filter out non-music items from songs
        val filteredSongs = songs
            .filter(::isMusicOnlySong)
            .distinctBy { it.id }
            .sortedByDescending { searchRelevanceScore(cleanQuery, it) }
            
        val distinctVideos = videos.distinctBy { it.id }

        val distinctAlbums = albums.distinctBy { it.browseId }
        val distinctArtists = artists
            .groupBy { it.browseId }
            .values
            .map { matches ->
                matches.maxByOrNull { artist ->
                    (if (!artist.thumbnailUrl.isNullOrBlank()) 2 else 0) +
                        (if (!artist.subscriberCount.isNullOrBlank()) 1 else 0)
                } ?: matches.first()
            }

        YouTubeSearchResult(
            songs = filteredSongs,
            albums = distinctAlbums,
            artists = distinctArtists,
            videos = distinctVideos
        )
    }

    private fun isMusicOnlySong(song: Song): Boolean {
        val metadata = "${song.title} ${song.artist} ${song.album}".lowercase()
        return song.title.isNotBlank() &&
            song.artist.isNotBlank() &&
            NON_MUSIC_KEYWORDS.none(metadata::contains)
    }

    private fun searchStructuredEndpoint(
        query: String,
        region: String,
        songs: MutableList<Song>,
        albums: MutableList<YouTubeAlbum>,
        artists: MutableList<YouTubeArtist>,
        videos: MutableList<Song>,
        params: String? = null,
    ) {
        try {
            val responseBody = executeWebRemixRequest("search", region) { config ->
                JSONObject().apply {
                    put("context", createWebRemixContext(region, config))
                    put("query", query)
                    if (!params.isNullOrBlank()) put("params", params)
                }
            } ?: return
            parseStructuredSearchResponse(responseBody, songs, albums, artists, videos)
        } catch (e: Exception) {
            Log.w(TAG, "Structured search failed for '$query': ${e.message}")
        }
    }

    /**
     * Search YouTube Music for songs, tracks, artists, and remixes with multi-tier fallback.
     */
    suspend fun search(query: String, region: String = "IN"): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val tracks = mutableListOf<YouTubeTrack>()

        // Tier 1: YouTube Music WEB_REMIX
        try {
            executeWebRemixRequest("search", region) { config ->
                JSONObject().apply {
                    put("context", createWebRemixContext(region, config))
                    put("query", cleanQuery)
                }
            }?.let { bodyString -> tracks.addAll(parseSearchResponse(bodyString)) }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 1 search failed: ${e.message}")
        }

        tracks.filter { track ->
            val lower = track.title.lowercase()
            NON_MUSIC_KEYWORDS.none { lower.contains(it) }
        }
            .distinctBy { it.videoId }
            .sortedByDescending { searchRelevanceScore(cleanQuery, it) }
    }

    /** Returns live query completions from YouTube Music, without requiring an account. */
    suspend fun getSearchSuggestions(query: String, region: String = "IN"): List<String> =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) return@withContext emptyList()
            runCatching {
                executeWebRemixRequest("music/get_search_suggestions", region) { config ->
                    JSONObject()
                        .put("context", createWebRemixContext(region, config))
                        .put("input", cleanQuery)
                }?.let { parseSearchSuggestions(it, cleanQuery) }.orEmpty()
            }.onFailure { error ->
                Log.w(TAG, "YouTube Music suggestions failed: ${error.message}")
            }.getOrDefault(emptyList())
        }

    /**
     * Builds YouTube Music's watch-next/radio continuation for one seed track.
     * This is intentionally independent from search so a tapped search result does not turn
     * the remaining keyword matches into the playback queue.
     */
    suspend fun getAutoplayTracks(seedVideoId: String, region: String = "IN"): List<YouTubeTrack> =
        withContext(Dispatchers.IO) {
            val cleanId = seedVideoId.removePrefix("yt_").trim()
            if (cleanId.isBlank()) return@withContext emptyList()
            runCatching {
                executeWebRemixRequest("next", region) { config ->
                    JSONObject().apply {
                        put("context", createWebRemixContext(region, config))
                        put("videoId", cleanId)
                        put("playlistId", "RDAMVM$cleanId")
                        put("isAudioOnly", true)
                        put("enablePersistentPlaylistPanel", true)
                        put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
                    }
                }?.let(::parseSearchResponse).orEmpty()
                    .filter(::isMusicTrack)
                    .filterNot { it.videoId == cleanId }
                    .distinctBy { it.videoId }
                    .take(50)
            }.onFailure { error ->
                Log.w(TAG, "Autoplay continuation failed for $cleanId: ${error.message}")
            }.getOrDefault(emptyList())
        }

    suspend fun getTrackDetails(videoId: String, region: String = "IN"): YouTubeTrack? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_").trim()
        if (cleanId.isBlank()) return@withContext null
        val musicDetails = runCatching {
            executeWebRemixRequest("next", region) { config ->
                JSONObject().apply {
                    put("context", createWebRemixContext(region, config))
                    put("videoId", cleanId)
                    put("isAudioOnly", true)
                }
            }?.let(::parseSearchResponse).orEmpty()
                .firstOrNull { it.videoId == cleanId }
        }.getOrNull()
        if (musicDetails != null) return@withContext musicDetails

        // `next` occasionally omits the selected item. Resolve the immutable YouTube id
        // through YouTube's public metadata endpoint instead of exposing placeholders.
        runCatching {
            val request = Request.Builder()
                .url("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$cleanId&format=json")
                .header("User-Agent", WEB_REMIX_USER_AGENT)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body.string())
                val rawTitle = json.optString("title").trim()
                val author = json.optString("author_name").removeSuffix(" - Topic").trim()
                if (rawTitle.isBlank() || author.isBlank()) null else YouTubeTrack(
                    videoId = cleanId,
                    title = rawTitle,
                    artist = author,
                    thumbnailUrl = json.optString("thumbnail_url").takeIf(String::isNotBlank)
                        ?: "https://i.ytimg.com/vi/$cleanId/maxresdefault.jpg",
                    resultType = YouTubeMusicEntityType.MUSIC_VIDEO,
                    isOfficial = true,
                )
            }
        }.getOrNull()
    }

    private fun isMusicTrack(track: YouTubeTrack): Boolean {
        val metadata = "${track.title} ${track.artist} ${track.album}".lowercase()
        return track.title.isNotBlank() && track.artist.isNotBlank() &&
            NON_MUSIC_KEYWORDS.none(metadata::contains)
    }

    /**
     * Fetch real-time Top Charts and Trending tracks for India / Global.
     */
    suspend fun getTrendingTracks(region: String = "IN"): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<YouTubeTrack>()

        // Tier 1: YouTube Music Charts Browse
        try {
            val jsonPayload = JSONObject().apply {
                put("context", createWebRemixContext(region))
                put("browseId", "FEmusic_charts")
            }

            val request = Request.Builder()
                .url("$INNERTUBE_MUSIC_BASE/browse?prettyPrint=false")
                .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-VYBE-Public-YouTube", "1")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string().orEmpty()
                tracks.addAll(parseBrowseResponse(bodyString))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Browse charts failed: ${e.message}")
        }

        val official = tracks.distinctBy { it.videoId }
            .filter { isCleanTrendingTrack(it.title, it.artist) }
        if (official.isNotEmpty()) {
            saveOfficialChart(region, official)
            official
        } else {
            // Never label broad text-search results as an official chart. Reuse the last
            // successfully parsed provider-ranked snapshot when the chart endpoint is down.
            loadOfficialChart(region)
        }
    }

    private fun saveOfficialChart(region: String, tracks: List<YouTubeTrack>) {
        val payload = JSONArray().apply {
            tracks.take(100).forEach { track ->
                put(JSONObject().apply {
                    put("id", track.videoId)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("duration", track.durationSeconds)
                    put("thumbnail", track.thumbnailUrl)
                    put("type", track.resultType.name)
                    put("official", track.isOfficial)
                })
            }
        }
        context.getSharedPreferences("ytm_official_charts", Context.MODE_PRIVATE).edit()
            .putString("chart_${region.uppercase()}", payload.toString())
            .putLong("chart_${region.uppercase()}_fetched_at", System.currentTimeMillis())
            .apply()
    }

    private fun loadOfficialChart(region: String): List<YouTubeTrack> {
        val raw = context.getSharedPreferences("ytm_official_charts", Context.MODE_PRIVATE)
            .getString("chart_${region.uppercase()}", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        YouTubeTrack(
                            videoId = item.getString("id"),
                            title = item.getString("title"),
                            artist = item.getString("artist"),
                            album = item.optString("album", "YouTube Music"),
                            durationSeconds = item.optLong("duration"),
                            thumbnailUrl = item.optString("thumbnail").takeIf(String::isNotBlank),
                            resultType = runCatching {
                                YouTubeMusicEntityType.valueOf(item.optString("type"))
                            }.getOrDefault(YouTubeMusicEntityType.SONG),
                            isOfficial = item.optBoolean("official"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Fetches the real YouTube Music New Releases browse feed. */
    suspend fun getLatestReleases(region: String = "IN"): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<YouTubeTrack>()
        val releaseAlbums = mutableListOf<YouTubeAlbum>()
        runCatching {
            val payload = JSONObject().apply {
                put("context", createWebRemixContext(region))
                put("browseId", "FEmusic_new_releases")
            }
            val request = Request.Builder()
                .url("$INNERTUBE_MUSIC_BASE/browse?prettyPrint=false")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-VYBE-Public-YouTube", "1")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val payload = response.body?.string().orEmpty()
                    tracks += parseBrowseResponse(payload)
                    parseStructuredSearchResponse(
                        payload,
                        mutableListOf(),
                        releaseAlbums,
                        mutableListOf(),
                        mutableListOf()
                    )
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "New releases browse failed: ${error.message}")
        }

        // Resolve catalog albums because the browse cards themselves do not reliably carry
        // an exact date. Undated releases are deliberately excluded from Release Radar.
        if (releaseAlbums.isNotEmpty()) {
            tracks.clear()
            releaseAlbums.distinctBy { it.browseId }.take(30).forEach { album ->
                runCatching { getAlbumDetails(album.browseId) }
                    .getOrNull()
                    ?.let { details ->
                        details.tracks.firstOrNull()?.let { song ->
                            tracks += YouTubeTrack(
                                videoId = song.id.removePrefix("yt_"),
                                title = song.title,
                                artist = song.artist,
                                album = song.album,
                                durationSeconds = song.duration / 1_000L,
                                thumbnailUrl = song.albumArtUriString ?: album.thumbnailUrl,
                                isOfficial = true,
                                albumBrowseId = details.browseId,
                                releaseDateEpochMillis = details.releaseDateEpochMillis,
                            )
                        }
                    }
            }
        }

        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val oldestAllowed = today.minusDays(29)
        // Validate each card against the video's provider publication date. Album browse
        // responses may contain dates from unrelated recommendation shelves, which previously
        // allowed years-old tracks into Release Radar.
        val datedTracks = tracks.distinctBy { it.videoId }.chunked(6).flatMap { chunk ->
            coroutineScope {
                chunk.map { track ->
                    async(Dispatchers.IO) {
                        val published = fetchVideoPublishedDate(track.videoId, region)
                        val resolved = when {
                            track.releaseDateEpochMillis > 0L && published > 0L ->
                                minOf(track.releaseDateEpochMillis, published)
                            published > 0L -> published
                            else -> track.releaseDateEpochMillis
                        }
                        track.copy(releaseDateEpochMillis = resolved)
                    }
                }.awaitAll()
            }
        }
        datedTracks
            .filter { track -> NON_MUSIC_KEYWORDS.none { track.title.lowercase().contains(it) } }
            .filter { track ->
                if (track.releaseDateEpochMillis <= 0L) return@filter false
                val date = java.time.Instant.ofEpochMilli(track.releaseDateEpochMillis).atZone(zone).toLocalDate()
                !date.isBefore(oldestAllowed) && !date.isAfter(today)
            }
            .distinctBy { it.videoId }
            .sortedByDescending { it.releaseDateEpochMillis }
    }

    private fun fetchVideoPublishedDate(videoId: String, region: String): Long {
        if (videoId.isBlank()) return 0L
        return runCatching {
            val payload = JSONObject().apply {
                put("context", createWebRemixContext(region))
                put("videoId", videoId.removePrefix("yt_"))
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            val request = Request.Builder()
                .url("$INNERTUBE_MAIN_BASE/player?prettyPrint=false&key=$FALLBACK_WEB_REMIX_API_KEY")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", WEB_REMIX_USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use 0L
                val root = JSONObject(response.body?.string().orEmpty())
                val renderer = root.optJSONObject("microformat")
                    ?.optJSONObject("playerMicroformatRenderer")
                val raw = renderer?.optString("publishDate").orEmpty()
                    .ifBlank { renderer?.optString("uploadDate").orEmpty() }
                val date = runCatching { java.time.LocalDate.parse(raw.take(10)) }.getOrNull()
                    ?: return@use 0L
                date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }.getOrDefault(0L)
    }

    private fun isCleanTrendingTrack(title: String, artist: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowerArtist = artist.lowercase()
        val forbiddenKeywords = listOf(
            "dj", "remix", "mashup", "nonstop", "non-stop", "mix", "slowed", "reverb", "karaoke", "instrumental", "loop"
        )
        return forbiddenKeywords.none { keyword ->
            lowerTitle.contains(keyword) || lowerArtist.contains(keyword)
        }
    }

    /**
     * Resolve direct playable audio stream URL (Opus / AAC) with high availability multi-client failover.
     */
    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_")
        streamUrlCache[cleanId]?.takeIf { System.currentTimeMillis() - it.second < CACHE_TTL_MS }
            ?.first
            ?: resolveStreamUrlParallel(cleanId)
    }

    private suspend fun resolveStreamUrlParallel(cleanId: String): String? = coroutineScope {
        val results = Channel<String?>(Channel.UNLIMITED)
        val resolvers: List<() -> String?> = listOf(
            { resolveViaAndroidVr(cleanId) },
            { resolveViaAndroidTestSuite(cleanId) },
            { resolveViaTvEmbedded(cleanId) },
            { resolveViaIos(cleanId) },
            { resolveViaAndroidMusic(cleanId) },
            { newPipeStreamResolver.resolve(cleanId, preferEfficientStream()) },
        )
        resolvers.forEach { resolver ->
            launch(Dispatchers.IO) {
                val candidate = runCatching { resolver() }.getOrNull()
                results.send(candidate?.takeIf { it.isNotBlank() && validateStreamUrl(it) })
            }
        }
        val resolved = withTimeoutOrNull(8_000L) {
            repeat(resolvers.size) {
                results.receive()?.let { return@withTimeoutOrNull it }
            }
            null
        }
        coroutineContext.cancelChildren()
        results.close()
        resolved?.also { streamUrlCache[cleanId] = it to System.currentTimeMillis() }
    }

    /**
     * Synchronous stream resolution helper for background DataSource loader threads.
     */
    fun resolveStreamUrlSync(videoId: String): String? {
        return resolveStreamUrlInternal(videoId)
    }

    private fun resolveStreamUrlInternal(videoId: String): String? {
        val cleanId = videoId.removePrefix("yt_")

        // 1. Check in-memory cache
        val cached = streamUrlCache[cleanId]
        if (cached != null && (System.currentTimeMillis() - cached.second) < CACHE_TTL_MS) {
            return cached.first
        }

        // 2. Strategy A: Innertube ANDROID_VR (Direct unthrottled audio streams)
        val vrUrl = resolveViaAndroidVr(cleanId)
        if (!vrUrl.isNullOrBlank() && validateStreamUrl(vrUrl)) {
            Log.d(TAG, "Resolved stream via ANDROID_VR for $cleanId")
            streamUrlCache[cleanId] = vrUrl to System.currentTimeMillis()
            return vrUrl
        }

        // 3. Strategy B: Innertube ANDROID_TESTSUITE (Direct unthrottled streams)
        val testSuiteUrl = resolveViaAndroidTestSuite(cleanId)
        if (!testSuiteUrl.isNullOrBlank() && validateStreamUrl(testSuiteUrl)) {
            Log.d(TAG, "Resolved stream via ANDROID_TESTSUITE for $cleanId")
            streamUrlCache[cleanId] = testSuiteUrl to System.currentTimeMillis()
            return testSuiteUrl
        }

        // 4. Strategy C: Innertube TV Embedded HTML5 client
        val tvUrl = resolveViaTvEmbedded(cleanId)
        if (!tvUrl.isNullOrBlank() && validateStreamUrl(tvUrl)) {
            Log.d(TAG, "Resolved stream via TVHTML5 for $cleanId")
            streamUrlCache[cleanId] = tvUrl to System.currentTimeMillis()
            return tvUrl
        }

        // 5. Strategy D: Innertube iOS client
        val iosUrl = resolveViaIos(cleanId)
        if (!iosUrl.isNullOrBlank() && validateStreamUrl(iosUrl)) {
            Log.d(TAG, "Resolved stream via IOS for $cleanId")
            streamUrlCache[cleanId] = iosUrl to System.currentTimeMillis()
            return iosUrl
        }

        // 6. Strategy E: Innertube Android Music client
        val androidUrl = resolveViaAndroidMusic(cleanId)
        if (!androidUrl.isNullOrBlank() && validateStreamUrl(androidUrl)) {
            Log.d(TAG, "Resolved stream via ANDROID_MUSIC for $cleanId")
            streamUrlCache[cleanId] = androidUrl to System.currentTimeMillis()
            return androidUrl
        }

        // 6. Strategy F: NewPipe extractor fallback
        // YouTube changes its player clients and signature rules frequently. NewPipe's
        // maintained extractor handles those changes (including signature/n-parameter
        // deciphering) and gives us an audio-only progressive URL Media3 can consume.
        val extractorUrl = newPipeStreamResolver.resolve(cleanId, preferLowBitrate = preferEfficientStream())
        if (!extractorUrl.isNullOrBlank()) {
            Log.d(TAG, "Resolved stream via NewPipe for $cleanId")
            streamUrlCache[cleanId] = extractorUrl to System.currentTimeMillis()
            return extractorUrl
        }

        // 7. Strategy G: Piped instances failover
        for (pipedBase in PIPED_INSTANCES) {
            val pipedUrl = resolveViaPiped(cleanId, pipedBase)
            if (!pipedUrl.isNullOrBlank() && validateStreamUrl(pipedUrl)) {
                Log.d(TAG, "Resolved stream via Piped ($pipedBase) for $cleanId")
                streamUrlCache[cleanId] = pipedUrl to System.currentTimeMillis()
                return pipedUrl
            }
        }

        // 8. Strategy H: Invidious instances failover
        for (invidiousBase in INVIDIOUS_INSTANCES) {
            val invidiousUrl = resolveViaInvidious(cleanId, invidiousBase)
            if (!invidiousUrl.isNullOrBlank() && validateStreamUrl(invidiousUrl)) {
                Log.d(TAG, "Resolved stream via Invidious ($invidiousBase) for $cleanId")
                streamUrlCache[cleanId] = invidiousUrl to System.currentTimeMillis()
                return invidiousUrl
            }
        }

        return null
    }

    private fun resolveViaAndroidVr(videoId: String): String? {
        return try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_VR")
                        put("clientVersion", "1.60.19")
                        put("deviceMake", "Oculus")
                        put("deviceModel", "Quest 3")
                        put("androidSdkVersion", 32)
                        put("hl", "en")
                        put("gl", "US")
                        put("visitorData", visitorData)
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("$INNERTUBE_MAIN_BASE/player?prettyPrint=false")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; Quest 3) AppleWebKit/537.36 (KHTML, like Gecko) OculusBrowser/32.0.0.0.0 Safari/537.36")
                .header("X-Goog-Visitor-Id", visitorData)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                extractAudioUrlFromStreamingData(json.optJSONObject("streamingData"))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveViaAndroidTestSuite(videoId: String): String? {
        return try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_TESTSUITE")
                        put("clientVersion", "1.9")
                        put("androidSdkVersion", 30)
                        put("hl", "en")
                        put("gl", "US")
                        put("visitorData", visitorData)
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("$INNERTUBE_MAIN_BASE/player?prettyPrint=false")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "com.google.android.youtube.testsuite/1.9 (Linux; U; Android 11; en_US)")
                .header("X-Goog-Visitor-Id", visitorData)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                extractAudioUrlFromStreamingData(json.optJSONObject("streamingData"))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveViaAndroidMusic(videoId: String): String? {
        return try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID_MUSIC")
                        put("clientVersion", "6.42.52")
                        put("androidSdkVersion", 34)
                        put("hl", "en")
                        put("gl", "US")
                        put("visitorData", visitorData)
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("$INNERTUBE_MUSIC_BASE/player?prettyPrint=false")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; en_US) gzip")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "6.42.52")
                .header("X-Goog-Visitor-Id", visitorData)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                extractAudioUrlFromStreamingData(json.optJSONObject("streamingData"))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveViaTvEmbedded(videoId: String): String? {
        return try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                        put("clientVersion", "2.0")
                        put("hl", "en")
                        put("gl", "US")
                        put("visitorData", visitorData)
                    })
                    put("thirdParty", JSONObject().apply {
                        put("embedUrl", "https://www.youtube.com/watch?v=$videoId")
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("$INNERTUBE_MAIN_BASE/player?prettyPrint=false")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/5.0 TV Safari/538.1")
                .header("X-YouTube-Client-Name", "7")
                .header("X-YouTube-Client-Version", "2.0")
                .header("X-Goog-Visitor-Id", visitorData)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                extractAudioUrlFromStreamingData(json.optJSONObject("streamingData"))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveViaIos(videoId: String): String? {
        return try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", "19.34.2")
                        put("deviceMake", "Apple")
                        put("deviceModel", "iPhone16,2")
                        put("hl", "en")
                        put("gl", "US")
                        put("visitorData", visitorData)
                    })
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url("$INNERTUBE_MAIN_BASE/player?prettyPrint=false")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "com.google.ios.youtube/19.34.2 (iPhone16,2; U; CPU iOS 17_6_1 like Mac OS X; en_US)")
                .header("X-YouTube-Client-Name", "5")
                .header("X-YouTube-Client-Version", "19.34.2")
                .header("X-Goog-Visitor-Id", visitorData)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                extractAudioUrlFromStreamingData(json.optJSONObject("streamingData"))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveViaPiped(videoId: String, instanceUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url("$instanceUrl/streams/$videoId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = JSONObject(response.body?.string().orEmpty())
                val audioStreams = body.optJSONArray("audioStreams") ?: return null
                var bestUrl: String? = null
                val efficient = preferEfficientStream()
                var selectedBitrate = if (efficient) Int.MAX_VALUE else 0
                for (i in 0 until audioStreams.length()) {
                    val stream = audioStreams.getJSONObject(i)
                    val bitrate = stream.optInt("bitrate", 0)
                    val url = stream.optString("url")
                    if (url.isNotBlank() && bitrate > 0 &&
                        (if (efficient) bitrate <= selectedBitrate else bitrate >= selectedBitrate)
                    ) {
                        selectedBitrate = bitrate
                        bestUrl = url
                    }
                }
                bestUrl
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveViaInvidious(videoId: String, instanceUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url("$instanceUrl/api/v1/videos/$videoId")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = JSONObject(response.body?.string().orEmpty())
                val adaptiveFormats = body.optJSONArray("adaptiveFormats") ?: return null
                var bestUrl: String? = null
                val efficient = preferEfficientStream()
                var selectedBitrate = if (efficient) Int.MAX_VALUE else 0
                for (i in 0 until adaptiveFormats.length()) {
                    val stream = adaptiveFormats.getJSONObject(i)
                    val type = stream.optString("type", "")
                    if (type.startsWith("audio/")) {
                        val bitrate = stream.optInt("bitrate", 0)
                        val url = stream.optString("url")
                        if (url.isNotBlank() && bitrate > 0 &&
                            (if (efficient) bitrate <= selectedBitrate else bitrate >= selectedBitrate)
                        ) {
                            selectedBitrate = bitrate
                            bestUrl = url
                        }
                    }
                }
                bestUrl
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractAudioUrlFromStreamingData(streamingData: JSONObject?): String? {
        if (streamingData == null) return null
        val formats = streamingData.optJSONArray("adaptiveFormats") ?: streamingData.optJSONArray("formats") ?: return null

        var bestUrl: String? = null
        val efficient = preferEfficientStream()
        var selectedBitrate = if (efficient) Int.MAX_VALUE else 0

        for (i in 0 until formats.length()) {
            val format = formats.getJSONObject(i)
            val mimeType = format.optString("mimeType", "")
            val url = format.optString("url", "")
            val bitrate = format.optInt("bitrate", 0)

            // Skip formats with blank URL (e.g. signatureCipher/cipher formats) since we don't support JS signature deciphering
            if (mimeType.startsWith("audio/") && url.isNotBlank()) {
                if (bitrate > 0 &&
                    (if (efficient) bitrate < selectedBitrate else bitrate > selectedBitrate)
                ) {
                    selectedBitrate = bitrate
                    bestUrl = url
                }
            }
        }
        return bestUrl
    }

    private fun parseCipher(cipher: String): String {
        return try {
            val params = cipher.split("&").associate {
                val parts = it.split("=")
                val key = parts.getOrNull(0) ?: ""
                val value = java.net.URLDecoder.decode(parts.getOrNull(1) ?: "", "UTF-8")
                key to value
            }
            val rawUrl = params["url"] ?: return ""
            val sig = params["s"] ?: params["sig"] ?: ""
            val sp = params["sp"] ?: "sig"
            if (sig.isNotBlank()) {
                if (rawUrl.contains("?")) "$rawUrl&$sp=$sig" else "$rawUrl?$sp=$sig"
            } else {
                rawUrl
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun validateStreamUrl(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            response.close()
            code in 200..399
        } catch (e: Exception) {
            true // Don't block if HEAD isn't supported by the server
        }
    }

    /**
     * Fetch complete artist profile: header, bio, top songs, albums/singles, related artists.
     */
    suspend fun getArtistProfile(browseId: String): YouTubeArtistProfile? = withContext(Dispatchers.IO) {
        val cleanBrowseId = if (browseId.startsWith("yt_artist_")) browseId.removePrefix("yt_artist_") else browseId
        try {
            val jsonPayload = JSONObject().apply {
                put("context", createWebRemixContext("IN"))
                put("browseId", cleanBrowseId)
            }

            val request = Request.Builder()
                .url("$INNERTUBE_MUSIC_BASE/browse?prettyPrint=false")
                .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .build()

            val bodyString = okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string().orEmpty() else ""
            }
            if (bodyString.isBlank()) return@withContext null

            val baseProfile = parseArtistProfileResponse(cleanBrowseId, bodyString)
                ?: return@withContext null
            val expansionRequests = collectArtistExpansionRequests(JSONObject(bodyString), cleanBrowseId)
            if (expansionRequests.isEmpty()) return@withContext baseProfile

            val expansionPayloads = coroutineScope {
                expansionRequests.map { (expansionId, params) ->
                    async {
                        fetchBrowsePages(
                            browseId = expansionId,
                            params = params,
                            region = "IN",
                        )
                    }
                }.awaitAll().flatten()
            }

            val extraTracks = mutableListOf<YouTubeTrack>()
            val extraAlbums = mutableListOf<YouTubeAlbum>()
            val extraArtists = mutableListOf<YouTubeArtist>()
            expansionPayloads.forEach { payload ->
                collectTracksRecursively(JSONObject(payload), extraTracks)
                parseStructuredSearchResponse(
                    payload,
                    mutableListOf(),
                    extraAlbums,
                    extraArtists,
                    mutableListOf()
                )
            }
            val extraSongs = extraTracks.filter { it.resultType != YouTubeMusicEntityType.MUSIC_VIDEO }.map { it.toSong() }
            val extraVideos = extraTracks.filter { it.resultType == YouTubeMusicEntityType.MUSIC_VIDEO }.map { it.toSong() }

            val cleanArtistName = baseProfile.name.trim().lowercase()
            fun isCleanArtistContent(song: Song): Boolean {
                val title = song.title.lowercase()
                val isJukebox = title.contains("jukebox") ||
                    title.contains("mashup") ||
                    title.contains("non stop") ||
                    title.contains("nonstop") ||
                    title.contains("megamix") ||
                    title.contains("all songs") ||
                    title.contains("full album") ||
                    title.contains("hits collection") ||
                    title.contains("compilation")
                if (isJukebox || song.duration > 900_000L) return false
                if (cleanArtistName.isBlank() || cleanArtistName == "artist") return true
                val songArtist = song.artist.trim().lowercase()
                return songArtist.contains(cleanArtistName) || cleanArtistName.contains(songArtist) || title.contains(cleanArtistName)
            }

            return@withContext baseProfile.copy(
                topSongs = (baseProfile.topSongs + extraSongs)
                    .filter(::isMusicOnlySong)
                    .filter(::isCleanArtistContent)
                    .distinctBy { it.id },
                videos = (baseProfile.videos + extraVideos)
                    .filter(::isCleanArtistContent)
                    .distinctBy { it.id },
                albums = (baseProfile.albums + extraAlbums).distinctBy { it.browseId },
                relatedArtists = (baseProfile.relatedArtists + extraArtists)
                    .filter { it.browseId != cleanBrowseId }
                    .distinctBy { it.browseId },
            )
        } catch (e: Exception) {
            Log.e(TAG, "getArtistProfile failed: ${e.message}")
        }
        null
    }

    private fun collectArtistExpansionRequests(
        root: Any?,
        artistBrowseId: String,
    ): List<Pair<String, String?>> {
        val result = linkedSetOf<Pair<String, String?>>()
        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    for (key in listOf("moreContentButton", "bottomEndpoint")) {
                        val container = node.opt(key) ?: continue
                        val endpoint = findFirstBrowseEndpoint(container) ?: continue
                        val id = endpoint.optString("browseId")
                        if (id.isNotBlank() && id != artistBrowseId && !id.startsWith("MPRE")) {
                            result += id to endpoint.optString("params").takeIf(String::isNotBlank)
                        }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) visit(node.opt(keys.next()))
                }
                is JSONArray -> for (index in 0 until node.length()) visit(node.opt(index))
            }
        }
        visit(root)
        return result.toList()
    }

    private fun fetchBrowsePages(
        browseId: String,
        params: String?,
        region: String,
    ): List<String> {
        val pages = mutableListOf<String>()
        val visitedContinuations = mutableSetOf<String>()
        var continuation: String? = null
        do {
            val page = executeWebRemixRequest("browse", region) { config ->
                JSONObject().apply {
                    put("context", createWebRemixContext(region, config))
                    if (continuation.isNullOrBlank()) {
                        put("browseId", browseId)
                        if (!params.isNullOrBlank()) put("params", params)
                    } else {
                        put("continuation", continuation)
                    }
                }
            } ?: break
            pages += page
            continuation = findContinuationTokens(JSONObject(page))
                .firstOrNull { visitedContinuations.add(it) }
        } while (continuation != null && pages.size < 40)
        return pages
    }

    private fun findContinuationTokens(root: Any?): List<String> {
        val tokens = linkedSetOf<String>()
        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("continuationCommand")
                        ?.optString("token")
                        ?.takeIf(String::isNotBlank)
                        ?.let(tokens::add)
                    node.optJSONObject("nextContinuationData")
                        ?.optString("continuation")
                        ?.takeIf(String::isNotBlank)
                        ?.let(tokens::add)
                    val keys = node.keys()
                    while (keys.hasNext()) visit(node.opt(keys.next()))
                }
                is JSONArray -> for (index in 0 until node.length()) visit(node.opt(index))
            }
        }
        visit(root)
        return tokens.toList()
    }

    /**
     * Fetch complete album details: title, artist, cover, track list.
     */
    suspend fun getAlbumDetails(browseId: String): YouTubeAlbumDetails? = withContext(Dispatchers.IO) {
        val cleanBrowseId = if (browseId.startsWith("yt_album_")) browseId.removePrefix("yt_album_") else browseId
        try {
            val bodyString = executeWebRemixRequest("browse", "IN") { config ->
                JSONObject().apply {
                    put("context", createWebRemixContext("IN", config))
                    put("browseId", cleanBrowseId)
                }
            }
            if (!bodyString.isNullOrBlank()) return@withContext parseAlbumDetailsResponse(cleanBrowseId, bodyString)
        } catch (e: Exception) {
            Log.e(TAG, "getAlbumDetails failed: ${e.message}")
        }
        null
    }

    private fun parseStructuredSearchResponse(
        jsonString: String,
        songs: MutableList<Song>,
        albums: MutableList<YouTubeAlbum>,
        artists: MutableList<YouTubeArtist>,
        videos: MutableList<Song>
    ) {
        try {
            val root = JSONObject(jsonString)
            parseStructuredRecursive(root, songs, albums, artists, videos)
        } catch (e: Exception) {
            Log.e(TAG, "parseStructuredSearchResponse error: ${e.message}")
        }
    }

    private fun parseStructuredRecursive(
        json: Any?,
        songs: MutableList<Song>,
        albums: MutableList<YouTubeAlbum>,
        artists: MutableList<YouTubeArtist>,
        videos: MutableList<Song>
    ) {
        when (json) {
            is JSONObject -> {
                if (json.has("musicResponsiveListItemRenderer")) {
                    val item = json.optJSONObject("musicResponsiveListItemRenderer")!!
                    categorizeAndAddItem(item, songs, albums, artists, videos)
                } else if (json.has("musicTwoRowItemRenderer")) {
                    val item = json.optJSONObject("musicTwoRowItemRenderer")!!
                    categorizeTwoRowItem(item, albums, artists)
                }

                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    parseStructuredRecursive(json.opt(key), songs, albums, artists, videos)
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    parseStructuredRecursive(json.opt(i), songs, albums, artists, videos)
                }
            }
        }
    }

    private fun categorizeAndAddItem(
        item: JSONObject,
        songs: MutableList<Song>,
        albums: MutableList<YouTubeAlbum>,
        artists: MutableList<YouTubeArtist>,
        videos: MutableList<Song>
    ) {
        parseListItem(item)?.let { track ->
            if (track.resultType == YouTubeMusicEntityType.MUSIC_VIDEO) {
                videos.add(track.toSong())
            } else {
                songs.add(track.toSong())
            }
            collectSongArtists(item, artists)
            return
        }

        val navEndpoint = item.optJSONObject("navigationEndpoint")
        val browseEndpoint = navEndpoint?.optJSONObject("browseEndpoint")
            ?: findFirstBrowseEndpoint(item)
        val pageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")

        val browseId = browseEndpoint?.optString("browseId", "") ?: ""
        val title = responsiveColumnText(item, 0)
            .ifBlank { extractRunsText(item.optJSONObject("title")) }
        val subtitle = responsiveColumnText(item, 1)
            .ifBlank { extractRunsText(item.optJSONObject("subtitle")) }
        val thumb = extractThumbnail(item.optJSONObject("thumbnail"))

        when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") -> {
                if (title.isNotBlank() && browseId.isNotBlank()) {
                    artists.add(
                        YouTubeArtist(
                            browseId = browseId,
                            name = title,
                            subscriberCount = subtitle.takeIf { it.isNotBlank() },
                            thumbnailUrl = thumb
                        )
                    )
                }
            }
            pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE") -> {
                if (title.isNotBlank() && browseId.isNotBlank()) {
                    albums.add(
                        YouTubeAlbum(
                            browseId = browseId,
                            title = title,
                            artist = subtitle.substringBefore(" • ").ifBlank { "Various Artists" },
                            thumbnailUrl = thumb
                        )
                    )
                }
            }
            pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" -> Unit
            else -> Unit
        }
    }

    private fun responsiveColumnText(item: JSONObject, index: Int): String {
        val text = item.optJSONArray("flexColumns")
            ?.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        return extractRunsText(text)
    }

    /** Adds every linked singer/performer from a song result as an openable profile result. */
    private fun collectSongArtists(item: JSONObject, artists: MutableList<YouTubeArtist>) {
        val artistText = item.optJSONArray("flexColumns")
            ?.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?: item.optJSONObject("subtitle")
            ?: return
        val runs = artistText.optJSONArray("runs") ?: return
        for (index in 0 until runs.length()) {
            val run = runs.optJSONObject(index) ?: continue
            val name = run.optString("text").trim()
            val browseEndpoint = run.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint") ?: continue
            val browseId = browseEndpoint.optString("browseId")
            val pageType = browseEndpoint
                .optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")
                ?.optString("pageType")
            val isArtist = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                browseId.startsWith("UC") || browseId.startsWith("MPLA")
            if (isArtist && name.isNotBlank() && browseId.isNotBlank()) {
                artists.add(YouTubeArtist(browseId = browseId, name = name))
            }
        }
    }

    private fun findFirstBrowseEndpoint(root: Any?): JSONObject? {
        when (root) {
            is JSONObject -> {
                root.optJSONObject("browseEndpoint")?.let { return it }
                val keys = root.keys()
                while (keys.hasNext()) {
                    findFirstBrowseEndpoint(root.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> for (index in 0 until root.length()) {
                findFirstBrowseEndpoint(root.opt(index))?.let { return it }
            }
        }
        return null
    }

    private fun categorizeTwoRowItem(
        item: JSONObject,
        albums: MutableList<YouTubeAlbum>,
        artists: MutableList<YouTubeArtist>
    ) {
        val navEndpoint = item.optJSONObject("navigationEndpoint")
        val browseEndpoint = navEndpoint?.optJSONObject("browseEndpoint")
        val pageType = browseEndpoint?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType")
        val browseId = browseEndpoint?.optString("browseId", "") ?: ""

        val title = extractRunsText(item.optJSONObject("title"))
        val subtitle = extractRunsText(item.optJSONObject("subtitle"))
        val thumb = extractThumbnail(item.optJSONObject("thumbnailRenderer"))

        if (title.isBlank() || browseId.isBlank()) return

        if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC")) {
            artists.add(
                YouTubeArtist(
                    browseId = browseId,
                    name = title,
                    subscriberCount = subtitle.takeIf { it.isNotBlank() },
                    thumbnailUrl = thumb
                )
            )
        } else if (pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE")) {
            albums.add(
                YouTubeAlbum(
                    browseId = browseId,
                    title = title,
                    artist = subtitle.substringBefore(" • ").ifBlank { "YouTube Music" },
                    thumbnailUrl = thumb
                )
            )
        }
    }

    private fun parseArtistProfileResponse(browseId: String, jsonString: String): YouTubeArtistProfile? {
        try {
            val root = JSONObject(jsonString)
            val header = root.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer")
                ?: root.optJSONObject("header")?.optJSONObject("musicVisualHeaderRenderer")

            val artistName = extractRunsText(header?.optJSONObject("title"))
            val description = extractRunsText(header?.optJSONObject("description"))
            val subscribers = extractRunsText(header?.optJSONObject("subscriptionButton")
                ?.optJSONObject("subscribeButtonRenderer")?.optJSONObject("subscriberCountText"))

            val avatarUrl = extractThumbnail(header?.optJSONObject("thumbnail"))
            val bannerUrl = extractThumbnail(header?.optJSONObject("foregroundThumbnail")) ?: avatarUrl

            val topSongs = mutableListOf<Song>()
            val videos = mutableListOf<Song>()
            val albums = mutableListOf<YouTubeAlbum>()
            val singles = mutableListOf<YouTubeAlbum>()
            val relatedArtists = mutableListOf<YouTubeArtist>()

            val rawTracks = mutableListOf<YouTubeTrack>()
            collectTracksRecursively(root, rawTracks)
            
            val rawSongs = rawTracks.filter { it.resultType != YouTubeMusicEntityType.MUSIC_VIDEO }.map { it.toSong() }
            val rawVideos = rawTracks.filter { it.resultType == YouTubeMusicEntityType.MUSIC_VIDEO }.map { it.toSong() }

            val cleanName = artistName.trim().lowercase()
            fun isStrictlyThisArtist(s: Song): Boolean {
                val title = s.title.lowercase()
                val isJukebox = title.contains("jukebox") ||
                    title.contains("mashup") ||
                    title.contains("non stop") ||
                    title.contains("nonstop") ||
                    title.contains("megamix") ||
                    title.contains("all songs") ||
                    title.contains("full album") ||
                    title.contains("hits collection") ||
                    title.contains("compilation")
                if (isJukebox || s.duration > 900_000L) return false
                if (cleanName.isBlank() || cleanName == "artist") return true
                val a = s.artist.trim().lowercase()
                return a.contains(cleanName) || cleanName.contains(a) || title.contains(cleanName)
            }

            topSongs.addAll(rawSongs.filter(::isStrictlyThisArtist))
            videos.addAll(rawVideos.filter(::isStrictlyThisArtist))

            // Collect release sections
            parseStructuredRecursive(root, mutableListOf(), albums, relatedArtists, mutableListOf())

            return YouTubeArtistProfile(
                browseId = browseId,
                name = artistName.ifBlank { "Artist" },
                bannerUrl = bannerUrl,
                avatarUrl = avatarUrl,
                description = description.takeIf { it.isNotBlank() },
                subscribers = subscribers.takeIf { it.isNotBlank() },
                topSongs = topSongs.distinctBy { it.id },
                videos = videos.distinctBy { it.id },
                albums = albums.distinctBy { it.browseId },
                singles = singles.distinctBy { it.browseId },
                relatedArtists = relatedArtists.filter { it.browseId != browseId }.distinctBy { it.browseId }
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseArtistProfileResponse error: ${e.message}")
        }
        return null
    }

    private fun parseAlbumDetailsResponse(browseId: String, jsonString: String): YouTubeAlbumDetails? {
        try {
            val root = JSONObject(jsonString)
            val header = findFirstObjectForKeys(
                root,
                setOf(
                    "musicDetailHeaderRenderer",
                    "musicResponsiveHeaderRenderer",
                    "musicImmersiveHeaderRenderer",
                    "musicVisualHeaderRenderer",
                ),
            )

            val title = extractRunsText(header?.optJSONObject("title"))
            val subtitle = extractRunsText(header?.optJSONObject("subtitle"))
            val headerArtist = extractRunsText(header?.optJSONObject("straplineTextOne"))
                .ifBlank { subtitle.substringBefore("•").trim() }

            val year = Regex("\\b(19|20)\\d{2}\\b").find(subtitle)?.value?.toIntOrNull()
            // Restrict date parsing to the album header. Scanning the full response can pick a
            // date from a recommended album and incorrectly label an old release as new.
            val releaseDateEpochMillis = extractExactReleaseDate(header?.toString().orEmpty())
            val albumType = when {
                subtitle.contains("single", ignoreCase = true) -> "Single"
                Regex("\\bEP\\b", RegexOption.IGNORE_CASE).containsMatchIn(subtitle) -> "EP"
                else -> "Album"
            }
            val headerCoverUrl = extractBestThumbnail(header)

            val tracks = mutableListOf<Song>()
            val rawTracks = mutableListOf<YouTubeTrack>()
            collectTracksRecursively(root, rawTracks)

            val resolvedTitle = title.ifBlank {
                rawTracks.firstNotNullOfOrNull { track ->
                    track.album.takeUnless { it.isBlank() || it.equals("YouTube Music", ignoreCase = true) }
                }.orEmpty()
            }.ifBlank { "Album" }
            val resolvedArtist = headerArtist.ifBlank {
                rawTracks.map { it.artist.trim() }.filter(String::isNotBlank)
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
            }.ifBlank { "Various Artists" }
            val coverUrl = headerCoverUrl
                ?: rawTracks.firstNotNullOfOrNull { it.thumbnailUrl?.takeIf(String::isNotBlank) }

            rawTracks.forEachIndexed { index, track ->
                val song = track.toSong().copy(
                    album = resolvedTitle,
                    albumArtUriString = track.thumbnailUrl?.takeIf(String::isNotBlank) ?: coverUrl,
                    trackNumber = index + 1
                )
                tracks.add(song)
            }

            return YouTubeAlbumDetails(
                browseId = browseId,
                title = resolvedTitle,
                artist = resolvedArtist,
                year = year,
                coverUrl = coverUrl,
                albumType = albumType,
                trackCount = tracks.size,
                tracks = tracks.distinctBy { it.id },
                releaseDateEpochMillis = releaseDateEpochMillis,
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseAlbumDetailsResponse error: ${e.message}")
        }
        return null
    }

    /** Reads only an exact provider date. A year by itself is never accepted. */
    private fun extractExactReleaseDate(json: String): Long {
        val iso = Regex("\\\"(?:releaseDate|publishDate|uploadDate)\\\"\\s*:\\s*\\\"(\\d{4}-\\d{2}-\\d{2})")
            .find(json)?.groupValues?.getOrNull(1)
        val textual = Regex(
            "(?:Released(?: on)?|Release date)[:\\s]+([A-Z][a-z]+\\s+\\d{1,2},\\s+\\d{4})",
            RegexOption.IGNORE_CASE,
        ).find(json.replace("\\u0026", "&"))?.groupValues?.getOrNull(1)
        val date = iso?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: textual?.let {
                runCatching {
                    java.time.LocalDate.parse(
                        it,
                        java.time.format.DateTimeFormatter.ofPattern("MMMM d, uuuu", java.util.Locale.ENGLISH),
                    )
                }.getOrNull()
            }
            ?: return 0L
        return date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun findFirstObjectForKeys(root: Any?, keys: Set<String>): JSONObject? {
        when (root) {
            is JSONObject -> {
                for (key in keys) root.optJSONObject(key)?.let { return it }
                val iterator = root.keys()
                while (iterator.hasNext()) {
                    findFirstObjectForKeys(root.opt(iterator.next()), keys)?.let { return it }
                }
            }
            is JSONArray -> for (index in 0 until root.length()) {
                findFirstObjectForKeys(root.opt(index), keys)?.let { return it }
            }
        }
        return null
    }

    private fun extractBestThumbnail(root: Any?): String? {
        var bestUrl: String? = null
        var bestArea = -1L
        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val url = node.optString("url").takeIf {
                        it.contains("googleusercontent.com", true) ||
                            it.contains("ggpht.com", true) ||
                            it.contains("ytimg.com", true)
                    }
                    if (url != null) {
                        val area = node.optLong("width", 1L) * node.optLong("height", 1L)
                        if (area >= bestArea) {
                            bestArea = area
                            bestUrl = url
                        }
                    }
                    val iterator = node.keys()
                    while (iterator.hasNext()) visit(node.opt(iterator.next()))
                }
                is JSONArray -> for (index in 0 until node.length()) visit(node.opt(index))
            }
        }
        visit(root)
        return bestUrl?.let(::highQualityArtworkUrl)
    }

    /**
     * YouTube Music rotates its public WEB_REMIX client version. Search requests made with
     * the old hard-coded profile return an empty 404 response, so bootstrap the current
     * public profile from the Music homepage and cache it briefly. The bundled values are
     * only a network-failure fallback; a rejected request forces one fresh bootstrap/retry.
     */
    private fun getWebRemixConfig(forceRefresh: Boolean = false): WebRemixConfig {
        val now = System.currentTimeMillis()
        webRemixConfig?.takeIf { !forceRefresh && now - it.loadedAtMs < WEB_CONFIG_TTL_MS }
            ?.let { return it }

        return synchronized(this) {
            webRemixConfig?.takeIf {
                !forceRefresh && now - it.loadedAtMs < WEB_CONFIG_TTL_MS
            }?.let { return@synchronized it }

            val fallback = WebRemixConfig(
                apiKey = FALLBACK_WEB_REMIX_API_KEY,
                clientVersion = FALLBACK_WEB_REMIX_CLIENT_VERSION,
                visitorData = visitorData,
                loadedAtMs = now,
            )
            val resolved = runCatching {
                val request = Request.Builder()
                    .url("https://music.youtube.com/")
                    .header("User-Agent", WEB_REMIX_USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("X-VYBE-Public-YouTube", "1")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Music bootstrap failed (${response.code})")
                    val page = response.body?.string().orEmpty()
                    WebRemixConfig(
                        apiKey = API_KEY_REGEX.find(page)?.groupValues?.getOrNull(1)
                            ?.takeIf(String::isNotBlank) ?: fallback.apiKey,
                        clientVersion = CLIENT_VERSION_REGEX.find(page)?.groupValues?.getOrNull(1)
                            ?.takeIf(String::isNotBlank) ?: fallback.clientVersion,
                        visitorData = VISITOR_DATA_REGEX.find(page)?.groupValues?.getOrNull(1)
                            ?.takeIf(String::isNotBlank) ?: fallback.visitorData,
                        loadedAtMs = now,
                    )
                }
            }.onFailure { error ->
                Log.w(TAG, "Could not refresh YouTube Music web configuration: ${error.message}")
            }.getOrDefault(fallback)

            visitorData = resolved.visitorData
            webRemixConfig = resolved
            resolved
        }
    }

    private fun executeWebRemixRequest(
        endpoint: String,
        region: String,
        payload: (WebRemixConfig) -> JSONObject,
    ): String? {
        repeat(2) { attempt ->
            val config = getWebRemixConfig(forceRefresh = attempt > 0)
            val requestBuilder = Request.Builder()
                .url("$INNERTUBE_MUSIC_BASE/$endpoint?key=${config.apiKey}&prettyPrint=false")
                .post(payload(config).toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", WEB_REMIX_USER_AGENT)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-YouTube-Client-Name", WEB_REMIX_CLIENT_NAME_HEADER)
                .header("X-YouTube-Client-Version", config.clientVersion)
                .header("X-VYBE-Public-YouTube", "1")
            if (config.visitorData.isNotBlank()) {
                requestBuilder.header("X-Goog-Visitor-Id", config.visitorData)
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful && responseBody.isNotBlank()) return responseBody
                Log.w(TAG, "YouTube Music $endpoint failed (${response.code}), attempt ${attempt + 1}")
                if (response.code !in setOf(400, 403, 404)) return null
            }
        }
        return null
    }

    private fun createWebRemixContext(
        region: String,
        config: WebRemixConfig? = null,
    ): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", config?.clientVersion ?: "1.20240801.01.00")
                put("hl", "en")
                put("gl", region.uppercase())
                config?.visitorData?.takeIf(String::isNotBlank)?.let { put("visitorData", it) }
            })
        }
    }

    private data class WebRemixConfig(
        val apiKey: String,
        val clientVersion: String,
        val visitorData: String,
        val loadedAtMs: Long,
    )

    private fun createAndroidContext(region: String): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID")
                put("clientVersion", "19.34.42")
                put("androidSdkVersion", 34)
                put("hl", "en")
                put("gl", region.uppercase())
            })
        }
    }

    private fun parseSearchResponse(jsonString: String): List<YouTubeTrack> {
        val list = mutableListOf<YouTubeTrack>()
        try {
            val root = JSONObject(jsonString)
            collectTracksRecursively(root, list)
        } catch (e: Exception) {
            Log.e(TAG, "parseSearchResponse error: ${e.message}")
        }
        return list
    }

    private fun parseBrowseResponse(jsonString: String): List<YouTubeTrack> {
        val list = mutableListOf<YouTubeTrack>()
        try {
            val root = JSONObject(jsonString)
            collectTracksRecursively(root, list)
        } catch (e: Exception) {
            Log.e(TAG, "parseBrowseResponse error: ${e.message}")
        }
        return list
    }

    suspend fun getMoodTracks(moodBrowseId: String, region: String = "IN"): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<YouTubeTrack>()
        try {
            val bodyString = executeWebRemixRequest("browse", region) { config ->
                JSONObject().apply {
                    put("context", createWebRemixContext(region, config))
                    put("browseId", moodBrowseId)
                }
            }
            if (!bodyString.isNullOrBlank()) {
                // Try to find tracks directly on the mood page
                tracks.addAll(parseBrowseResponse(bodyString))
                
                // If the mood page only contains playlists, let's extract the first playlist browseId
                if (tracks.isEmpty()) {
                    val root = JSONObject(bodyString)
                    val playlistIds = mutableListOf<String>()
                    fun findPlaylists(d: Any?) {
                        when (d) {
                            is JSONObject -> {
                                val browseId = d.optJSONObject("navigationEndpoint")
                                    ?.optJSONObject("browseEndpoint")
                                    ?.optString("browseId")
                                if (browseId != null && (browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD"))) {
                                    playlistIds.add(browseId)
                                }
                                d.keys().forEach { findPlaylists(d.opt(it)) }
                            }
                            is JSONArray -> {
                                for (i in 0 until d.length()) findPlaylists(d.opt(i))
                            }
                        }
                    }
                    findPlaylists(root)
                    
                    // Fetch tracks from the first 2 playlists found
                    for (pid in playlistIds.distinct().take(2)) {
                        getAlbumDetails(pid)?.tracks?.let { playlistTracks ->
                            tracks.addAll(playlistTracks.map { YouTubeTrack(
                                videoId = it.id.removePrefix("yt_"),
                                title = it.title,
                                artist = it.artist,
                                album = it.album.ifBlank { "YouTube Music" },
                                thumbnailUrl = it.albumArtUriString,
                                durationSeconds = it.duration / 1000L,
                                isOfficial = true
                            )})
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMoodTracks failed for $moodBrowseId: ${e.message}")
        }
        tracks.distinctBy { it.videoId }
    }

    private fun parseSearchSuggestions(jsonString: String, query: String): List<String> {
        val suggestions = mutableListOf<String>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    value.optJSONObject("searchSuggestionRenderer")
                        ?.optJSONObject("suggestion")
                        ?.let(::extractRunsText)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(suggestions::add)
                    value.keys().forEach { key -> visit(value.opt(key)) }
                }
                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index))
            }
        }
        runCatching { visit(JSONObject(jsonString)) }
        return suggestions
            .distinctBy(String::lowercase)
            .filterNot { it.equals(query, ignoreCase = true) }
            .take(8)
    }

    private fun collectTracksRecursively(json: Any?, list: MutableList<YouTubeTrack>) {
        when (json) {
            is JSONObject -> {
                if (json.has("musicResponsiveListItemRenderer")) {
                    parseListItem(json.optJSONObject("musicResponsiveListItemRenderer")!!)?.let { list.add(it) }
                }
                if (json.has("musicTwoRowItemRenderer")) {
                    parseListItem(json.optJSONObject("musicTwoRowItemRenderer")!!)?.let { list.add(it) }
                }
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    collectTracksRecursively(json.opt(key), list)
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    collectTracksRecursively(json.opt(i), list)
                }
            }
        }
    }

    private fun parseListItem(item: JSONObject): YouTubeTrack? {
        var title = ""
        var artist = ""

        val flexColumns = item.optJSONArray("flexColumns")
        if (flexColumns != null && flexColumns.length() > 0) {
            val col1 = flexColumns.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            val col2 = flexColumns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            title = extractRunsText(col1?.optJSONObject("text"))
            artist = extractMusicArtist(col2?.optJSONObject("text"))
        }

        if (title.isBlank()) {
            title = extractRunsText(item.optJSONObject("title"))
        }
        if (artist.isBlank()) {
            artist = extractMusicArtist(item.optJSONObject("subtitle"))
        }

        var videoId: String? = null
        if (item.has("playlistItemData")) {
            videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
        }

        if (videoId.isNullOrBlank()) {
            val menu = item.optJSONObject("menu")?.optJSONObject("menuRenderer")?.optJSONArray("items")
            if (menu != null) {
                for (m in 0 until menu.length()) {
                    val nav = menu.optJSONObject(m)?.optJSONObject("menuNavigationItemRenderer")?.optJSONObject("navigationEndpoint")
                    val vid = nav?.optJSONObject("watchEndpoint")?.optString("videoId")
                    if (!vid.isNullOrBlank()) {
                        videoId = vid
                        break
                    }
                }
            }
        }

        if (videoId.isNullOrBlank()) {
            val nav = item.optJSONObject("navigationEndpoint")
            videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId")
        }

        if (videoId.isNullOrBlank()) {
            val doubleTap = item.optJSONObject("doubleTapEndpoint")
            videoId = doubleTap?.optJSONObject("watchEndpoint")?.optString("videoId")
        }

        if (videoId.isNullOrBlank() || title.isBlank()) return null

        val metadataText = buildString {
            append(title)
            append(' ')
            append(artist)
            append(' ')
            append(responsiveColumnText(item, 1))
            append(' ')
            append(extractRunsText(item.optJSONObject("subtitle")))
        }.lowercase()
        if (NON_MUSIC_KEYWORDS.any(metadataText::contains)) return null

        val musicVideoType = findFirstString(item, "musicVideoType")
        val isAlbumTrack = musicVideoType == "MUSIC_VIDEO_TYPE_ATV"
        val isOfficialMusicVideo = musicVideoType == "MUSIC_VIDEO_TYPE_OMV"
        val isExplicitMusicTrack = item.has("playlistItemData") && item.has("flexColumns")
        if (!isAlbumTrack && !isOfficialMusicVideo && !isExplicitMusicTrack) return null

        val thumbnail = extractThumbnail(item.optJSONObject("thumbnail"))
            ?: extractBestThumbnail(item)
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val linkedArtists = extractLinkedArtists(item)
        val albumLink = extractLinkedAlbum(item)

        return YouTubeTrack(
            videoId = videoId,
            title = title,
            artist = linkedArtists.joinToString(", ") { it.name }.ifBlank {
                artist.ifBlank { "YouTube Music" }
            },
            album = albumLink?.second ?: "YouTube Music",
            durationSeconds = extractDurationSeconds(item),
            thumbnailUrl = thumbnail,
            resultType = if (isOfficialMusicVideo) YouTubeMusicEntityType.MUSIC_VIDEO else YouTubeMusicEntityType.SONG,
            isOfficial = isAlbumTrack || isOfficialMusicVideo,
            linkedArtists = linkedArtists,
            albumBrowseId = albumLink?.first,
        )
    }

    private fun extractDurationSeconds(item: JSONObject): Long {
        val candidates = mutableListOf<String>()
        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val text = node.optString("text").trim()
                    val simpleText = node.optString("simpleText").trim()
                    if (text.isNotBlank()) candidates += text
                    if (simpleText.isNotBlank()) candidates += simpleText
                    node.keys().forEach { key -> visit(node.opt(key)) }
                }
                is org.json.JSONArray -> for (index in 0 until node.length()) visit(node.opt(index))
            }
        }
        visit(item.optJSONArray("fixedColumns"))
        visit(item.optJSONArray("flexColumns"))
        visit(item.optJSONObject("lengthText"))
        val duration = candidates.lastOrNull { DURATION_TEXT_REGEX.matches(it) } ?: return 0L
        return duration.split(':').fold(0L) { total, part -> total * 60L + (part.toLongOrNull() ?: 0L) }
    }

    private fun extractLinkedArtists(item: JSONObject): List<YouTubeArtist> {
        val candidates = buildList {
            val flexColumns = item.optJSONArray("flexColumns")
            if (flexColumns != null) {
                for (index in 0 until flexColumns.length()) {
                    flexColumns.optJSONObject(index)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.let(::add)
                }
            }
            item.optJSONObject("subtitle")?.let(::add)
        }
        return candidates.flatMap { text ->
            val runs = text.optJSONArray("runs") ?: return@flatMap emptyList()
            buildList {
                for (index in 0 until runs.length()) {
                    val run = runs.optJSONObject(index) ?: continue
                    val browseEndpoint = run.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint") ?: continue
                    val browseId = browseEndpoint.optString("browseId")
                    val pageType = browseEndpoint
                        .optJSONObject("browseEndpointContextSupportedConfigs")
                        ?.optJSONObject("browseEndpointContextMusicConfig")
                        ?.optString("pageType")
                    if (pageType == "MUSIC_PAGE_TYPE_ARTIST" || browseId.startsWith("UC") || browseId.startsWith("MPLA")) {
                        val name = run.optString("text").trim()
                        if (name.isNotBlank() && browseId.isNotBlank()) {
                            add(YouTubeArtist(browseId = browseId, name = name))
                        }
                    }
                }
            }
        }.distinctBy { it.browseId }
    }

    private fun extractLinkedAlbum(item: JSONObject): Pair<String, String>? {
        val flexColumns = item.optJSONArray("flexColumns") ?: return null
        for (columnIndex in 0 until flexColumns.length()) {
            val runs = flexColumns.optJSONObject(columnIndex)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs") ?: continue
            for (runIndex in 0 until runs.length()) {
                val run = runs.optJSONObject(runIndex) ?: continue
                val browseEndpoint = run.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint") ?: continue
                val browseId = browseEndpoint.optString("browseId")
                val pageType = browseEndpoint
                    .optJSONObject("browseEndpointContextSupportedConfigs")
                    ?.optJSONObject("browseEndpointContextMusicConfig")
                    ?.optString("pageType")
                if (pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE")) {
                    return browseId to run.optString("text").trim()
                }
            }
        }
        return null
    }

    private fun extractMusicArtist(textObj: JSONObject?): String {
        val runs = textObj?.optJSONArray("runs") ?: return extractRunsText(textObj)
            .split('•')
            .firstOrNull { it.trim().lowercase() !in RESULT_TYPE_LABELS }
            ?.trim()
            .orEmpty()
        val artistNames = buildList {
            for (index in 0 until runs.length()) {
                val run = runs.optJSONObject(index) ?: continue
                val value = run.optString("text").trim()
                val browseId = findFirstString(run, "browseId")
                if (value.isNotBlank() && (browseId?.startsWith("UC") == true || browseId?.startsWith("MPLA") == true)) {
                    add(value)
                }
            }
        }
        if (artistNames.isNotEmpty()) return artistNames.distinct().joinToString(", ")
        return extractRunsText(textObj)
            .split('•')
            .map(String::trim)
            .firstOrNull { it.isNotBlank() && it.lowercase() !in RESULT_TYPE_LABELS }
            .orEmpty()
    }

    private fun findFirstString(root: Any?, targetKey: String): String? {
        when (root) {
            is JSONObject -> {
                root.optString(targetKey).takeIf { it.isNotBlank() }?.let { return it }
                root.keys().forEach { key -> findFirstString(root.opt(key), targetKey)?.let { return it } }
            }
            is JSONArray -> for (index in 0 until root.length()) {
                findFirstString(root.opt(index), targetKey)?.let { return it }
            }
        }
        return null
    }

    private fun searchRelevanceScore(query: String, track: YouTubeTrack): Int {
        val wanted = normalizeSearchText(query)
        val title = normalizeSearchText(track.title)
        val artist = normalizeSearchText(track.artist)
        val exactTitle = when {
            title == wanted -> 120
            title.startsWith(wanted) -> 90
            title.contains(wanted) -> 70
            else -> 0
        }
        val tokenCoverage = wanted.split(' ').filter(String::isNotBlank)
            .count { token -> token in title || token in artist } * 8
        return exactTitle + tokenCoverage + (if (track.isOfficial) 35 else 0) +
            (if (track.resultType == YouTubeMusicEntityType.SONG) 10 else 0)
    }

    private fun searchRelevanceScore(query: String, song: Song): Int {
        val wanted = normalizeSearchText(query)
        val title = normalizeSearchText(song.title)
        val artist = normalizeSearchText(song.displayArtist)
        val exactTitle = when {
            title == wanted -> 1_000
            title.startsWith(wanted) -> 750
            title.contains(wanted) -> 550
            wanted.startsWith(title) && title.length >= 4 -> 400
            else -> 0
        }
        val tokens = wanted.split(' ').filter { it.length > 1 }
        val coverage = tokens.count { it in title || it in artist } * 45
        val missingPenalty = tokens.count { it !in title && it !in artist } * 60
        val providerBonus = if (song.id.startsWith("yt_")) 80 else 0
        return exactTitle + coverage + providerBonus - missingPenalty
    }

    private fun normalizeSearchText(value: String): String = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun extractRunsText(textObj: JSONObject?): String {
        if (textObj == null) return ""
        val runs = textObj.optJSONArray("runs") ?: return textObj.optString("simpleText", "")
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", ""))
        }
        return sb.toString().trim()
    }

    private fun extractThumbnail(thumbnailObj: JSONObject?): String? {
        val renderer = thumbnailObj?.optJSONObject("musicThumbnailRenderer")
            ?: thumbnailObj?.optJSONObject("croppedSquareThumbnailRenderer")
        val thumbnails = renderer?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: thumbnailObj?.optJSONArray("thumbnails") ?: return null
        if (thumbnails.length() == 0) return null
        val last = thumbnails.optJSONObject(thumbnails.length() - 1)
        val url = last?.optString("url") ?: return null
        return highQualityArtworkUrl(url)
    }

    private fun highQualityArtworkUrl(url: String): String {
        if (url.isBlank()) return url
        return when {
            "googleusercontent.com" in url || "ggpht.com" in url -> url
                .replace(Regex("=w\\d+-h\\d+[^?&]*"), "=w1200-h1200-l90-rj")
                .replace(Regex("=s\\d+[^?&]*"), "=s1200")
            "ytimg.com/vi/" in url -> url.replace("/hqdefault.jpg", "/sddefault.jpg")
            else -> url
        }
    }

}
