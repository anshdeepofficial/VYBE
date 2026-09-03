package com.theveloper.pixelplay.data.spotify

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.theveloper.pixelplay.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class SpotifyTasteProfile(
    val playlistCount: Int = 0,
    val savedTrackCount: Int = 0,
    val topTrackCount: Int = 0,
    val topArtistCount: Int = 0,
)

data class SpotifyRemotePlaylist(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val ownerName: String,
    val canImportItems: Boolean = true,
)

data class SpotifyAccountTrack(
    val key: String,
    val title: String,
    val artists: String,
    val album: String,
    val albumArtUrl: String?,
    val durationMs: Long,
)

data class SpotifyAccountPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val tracks: List<SpotifyAccountTrack>,
)

/** Official Spotify Web API + OAuth PKCE + Web Session account integration. */
@Singleton
class SpotifyAccountRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
) {
    private val preferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (t: Throwable) {
        timber.log.Timber.e(t, "SpotifyAccountRepository: Keystore failed. Falling back to plain SharedPreferences.")
        context.getSharedPreferences(PREFS_NAME + "_plain", Context.MODE_PRIVATE)
    }

    private val _isLoggedIn = MutableStateFlow(hasUsableSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    private val _accountName = MutableStateFlow(preferences.getString(KEY_ACCOUNT_NAME, null).orEmpty())
    val accountName: StateFlow<String> = _accountName.asStateFlow()
    private val _tasteProfile = MutableStateFlow(SpotifyTasteProfile())
    val tasteProfile: StateFlow<SpotifyTasteProfile> = _tasteProfile.asStateFlow()
    private val _playlists = MutableStateFlow<List<SpotifyRemotePlaylist>>(emptyList())
    val playlists: StateFlow<List<SpotifyRemotePlaylist>> = _playlists.asStateFlow()

    val isConfigured: Boolean get() = true

    fun createAuthorizationUri(): Uri {
        check(BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()) { "Add SPOTIFY_CLIENT_ID to local.properties for Spotify sign-in." }
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(32)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        preferences.edit()
            .putString(KEY_PENDING_VERIFIER, verifier)
            .putString(KEY_PENDING_STATE, state)
            .apply()
        return Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .build()
    }

    suspend fun loginWithSpDc(spDc: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCookie = spDc.trim().removePrefix("sp_dc=").trim()
            require(cleanCookie.isNotBlank()) { "Invalid Spotify session cookie." }
            preferences.edit().putString(KEY_SP_DC, cleanCookie).apply()
            val tokenObj = fetchWebPlayerToken(cleanCookie)
            val accessToken = tokenObj.getString("accessToken")
            val expMs = tokenObj.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 3600_000L)
            preferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_EXPIRES_AT, expMs)
                .apply()
            refreshProfileAndTaste()
            _isLoggedIn.value = true
        }.onFailure {
            _isLoggedIn.value = false
            preferences.edit().remove(KEY_SP_DC).apply()
        }
    }

    private fun fetchWebPlayerToken(spDc: String): JSONObject {
        val req = Request.Builder()
            .url("https://open.spotify.com/get_access_token?reason=transport&productType=web_player")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            .header("Cookie", "sp_dc=$spDc")
            .header("Accept", "application/json")
            .build()
        val responseBody = execute(req)
        val json = JSONObject(responseBody)
        if (json.optBoolean("isAnonymous", false) || !json.has("accessToken")) {
            error("Invalid or expired Spotify session cookie.")
        }
        return json
    }

    suspend fun completeAuthorization(callback: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            callback.getQueryParameter("error")?.let { error("Spotify authorization failed: $it") }
            val expectedState = preferences.getString(KEY_PENDING_STATE, null)
            val actualState = callback.getQueryParameter("state")
            check(!expectedState.isNullOrBlank() && expectedState == actualState) {
                "Spotify sign-in state did not match. Please try again."
            }
            val verifier = preferences.getString(KEY_PENDING_VERIFIER, null)
                ?: error("Spotify sign-in session expired. Please try again.")
            val code = callback.getQueryParameter("code")
                ?: error("Spotify did not return an authorization code.")
            val token = exchangeToken(
                FormBody.Builder()
                    .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
                    .add("code_verifier", verifier)
                    .build()
            )
            persistToken(token)
            preferences.edit().remove(KEY_PENDING_STATE).remove(KEY_PENDING_VERIFIER).apply()
            // A valid Spotify login must not be rejected just because an optional
            // taste endpoint is unavailable for this account/app mode.
            refreshProfileAndTaste()
            _isLoggedIn.value = true
        }.onFailure { _isLoggedIn.value = false }
    }

    suspend fun refreshProfileAndTaste(): SpotifyTasteProfile = withContext(Dispatchers.IO) {
        val profile = authorizedGet("/me")
        val accountId = profile.optString("account_id").ifBlank { profile.optString("id") }
        val displayName = profile.optString("display_name").ifBlank { "Spotify User" }
        val playlists = optionalAuthorizedGet("/me/playlists?limit=1")?.optInt("total") ?: 0
        val saved = optionalAuthorizedGet("/me/tracks?limit=1")?.optInt("total") ?: 0
        val topTracks = optionalAuthorizedGet("/me/top/tracks?limit=10&time_range=medium_term")
            ?.optJSONArray("items")?.length() ?: 0
        val topArtists = optionalAuthorizedGet("/me/top/artists?limit=10&time_range=medium_term")
            ?.optJSONArray("items")?.length() ?: 0
        val taste = SpotifyTasteProfile(playlists, saved, topTracks, topArtists)
        preferences.edit()
            .putString(KEY_ACCOUNT_NAME, displayName)
            .putString(KEY_ACCOUNT_ID, accountId)
            .apply()
        _accountName.value = displayName
        _tasteProfile.value = taste
        _isLoggedIn.value = true
        taste
    }

    suspend fun saveDirectAccessToken(token: String): SpotifyTasteProfile = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, cleanToken)
            .remove(KEY_REFRESH_TOKEN)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (3600 * 1000L))
            .apply()
        refreshProfileAndTaste()
    }

    suspend fun getCurrentUserPlaylists(): List<SpotifyRemotePlaylist> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyRemotePlaylist>()

        // Also fetch user's Top Songs / Best Songs curated by Spotify
        runCatching {
            val topTracksLong = optionalAuthorizedGet("/me/top/tracks?time_range=long_term&limit=50")
            val topItemsLong = topTracksLong?.optJSONArray("items")
            if (topItemsLong != null && topItemsLong.length() > 0) {
                result += SpotifyRemotePlaylist(
                    id = "spotify_top_tracks_long_term",
                    name = "Your Top Songs (All-Time / Best Songs)",
                    coverUrl = topItemsLong.optJSONObject(0)?.optJSONObject("album")
                        ?.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                    trackCount = topItemsLong.length(),
                    ownerName = "Spotify",
                    canImportItems = true,
                )
            }

            val topTracksMedium = optionalAuthorizedGet("/me/top/tracks?time_range=medium_term&limit=50")
            val topItemsMedium = topTracksMedium?.optJSONArray("items")
            if (topItemsMedium != null && topItemsMedium.length() > 0) {
                result += SpotifyRemotePlaylist(
                    id = "spotify_top_tracks_medium_term",
                    name = "Your Top Songs (Recent / 2024-2025)",
                    coverUrl = topItemsMedium.optJSONObject(0)?.optJSONObject("album")
                        ?.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                    trackCount = topItemsMedium.length(),
                    ownerName = "Spotify",
                    canImportItems = true,
                )
            }

            val savedTracks = optionalAuthorizedGet("/me/tracks?limit=1")
            val savedCount = savedTracks?.optInt("total", 0) ?: 0
            if (savedCount > 0) {
                result += SpotifyRemotePlaylist(
                    id = "spotify_saved_liked_songs",
                    name = "Liked Songs (Spotify)",
                    coverUrl = "https://misc.scdn.co/liked-songs/liked-songs-300.png",
                    trackCount = savedCount,
                    ownerName = "Spotify",
                    canImportItems = true,
                )
            }
        }

        var offset = 0
        val webApiResult = runCatching {
            do {
                val page = authorizedGet("/me/playlists?limit=$PLAYLIST_PAGE_SIZE&offset=$offset")
                val items = page.optJSONArray("items") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isEmpty()) continue
                    val owner = item.optJSONObject("owner")
                    result += SpotifyRemotePlaylist(
                        id = id,
                        name = item.optString("name").ifBlank { "Spotify Playlist" },
                        coverUrl = item.optJSONArray("images")?.optJSONObject(0)
                            ?.optString("url")?.takeIf(String::isNotBlank),
                        trackCount = item.optJSONObject("items")?.optInt("total")
                            ?: item.optJSONObject("tracks")?.optInt("total")
                            ?: 0,
                        ownerName = owner?.optString("display_name")
                            ?.ifBlank { owner.optString("id") }
                            .orEmpty(),
                        canImportItems = true,
                    )
                }
                offset += items.length()
            } while (items.length() > 0 && offset < page.optInt("total", offset))
            result.distinctBy { it.id }
        }

        if (webApiResult.isFailure) {
            val exception = webApiResult.exceptionOrNull()
            val msg = exception?.message.orEmpty()
            if (msg.contains("403") || msg.contains("denied", ignoreCase = true) || msg.contains("Development Mode", ignoreCase = true)) {
                // In Development Mode without user dashboard registration, Spotify restricts direct /me/playlists access.
                // Return whatever curated top songs were fetched safely.
                val fallback = result.distinctBy { it.id }
                _playlists.value = fallback
                return@withContext fallback
            }
            if (result.isNotEmpty()) {
                val fallback = result.distinctBy { it.id }
                _playlists.value = fallback
                return@withContext fallback
            }
            throw exception ?: Exception("Could not load Spotify playlists")
        }

        result.distinctBy { it.id }.also { _playlists.value = it }
    }

    suspend fun getPlaylistForImport(playlistId: String): SpotifyAccountPlaylist = withContext(Dispatchers.IO) {
        if (playlistId.startsWith("spotify_top_tracks_")) {
            val term = playlistId.removePrefix("spotify_top_tracks_")
            val topTracksJson = authorizedGet("/me/top/tracks?time_range=$term&limit=50")
            val items = topTracksJson.optJSONArray("items") ?: JSONArray()
            val tracks = mutableListOf<SpotifyAccountTrack>()
            for (index in 0 until items.length()) {
                val track = items.optJSONObject(index) ?: continue
                val title = track.optString("name").trim()
                if (title.isEmpty()) continue
                val artistsJson = track.optJSONArray("artists") ?: JSONArray()
                val artists = buildList {
                    for (artistIndex in 0 until artistsJson.length()) {
                        artistsJson.optJSONObject(artistIndex)?.optString("name")
                            ?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }.joinToString(", ")
                val album = track.optJSONObject("album")
                val id = track.optString("id").ifBlank { track.optString("uri") }
                tracks += SpotifyAccountTrack(
                    key = "$id:$index",
                    title = title,
                    artists = artists,
                    album = album?.optString("name").orEmpty(),
                    albumArtUrl = album?.optJSONArray("images")?.optJSONObject(0)
                        ?.optString("url")?.takeIf(String::isNotBlank),
                    durationMs = track.optLong("duration_ms", 0L),
                )
            }
            if (tracks.isEmpty()) error("No top tracks found for import.")
            return@withContext SpotifyAccountPlaylist(
                id = playlistId,
                name = if (term == "long_term") "Your Top Songs (All-Time / Best Songs)" else "Your Top Songs (Recent)",
                coverUrl = tracks.firstOrNull()?.albumArtUrl,
                tracks = tracks,
            )
        }
        if (playlistId == "spotify_saved_liked_songs") {
            val tracks = mutableListOf<SpotifyAccountTrack>()
            var offset = 0
            var total = Int.MAX_VALUE
            while (offset < total && offset < 500) {
                val page = authorizedGet("/me/tracks?limit=50&offset=$offset")
                val items = page.optJSONArray("items") ?: JSONArray()
                total = page.optInt("total", 0)
                for (index in 0 until items.length()) {
                    val wrapper = items.optJSONObject(index) ?: continue
                    val track = wrapper.optJSONObject("track") ?: continue
                    val title = track.optString("name").trim()
                    if (title.isEmpty()) continue
                    val artistsJson = track.optJSONArray("artists") ?: JSONArray()
                    val artists = buildList {
                        for (i in 0 until artistsJson.length()) {
                            artistsJson.optJSONObject(i)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
                        }
                    }.joinToString(", ")
                    val album = track.optJSONObject("album")
                    val id = track.optString("id").ifBlank { track.optString("uri") }
                    tracks += SpotifyAccountTrack(
                        key = "$id:${offset + index}",
                        title = title,
                        artists = artists,
                        album = album?.optString("name").orEmpty(),
                        albumArtUrl = album?.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                        durationMs = track.optLong("duration_ms", 0L),
                    )
                }
                if (items.length() == 0) break
                offset += items.length()
            }
            return@withContext SpotifyAccountPlaylist(
                id = playlistId,
                name = "Liked Songs (Spotify)",
                coverUrl = "https://misc.scdn.co/liked-songs/liked-songs-300.png",
                tracks = tracks,
            )
        }
        require(playlistId.matches(Regex("[A-Za-z0-9_]+"))) { "Invalid Spotify playlist ID" }
        val metadata = authorizedGet("/playlists/$playlistId")
        val tracks = mutableListOf<SpotifyAccountTrack>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total) {
            val page = authorizedGet(
                "/playlists/$playlistId/items?limit=$TRACK_PAGE_SIZE&offset=$offset&additional_types=track"
            )
            val items = page.optJSONArray("items") ?: JSONArray()
            total = page.optInt("total", 0)
            for (index in 0 until items.length()) {
                val wrapper = items.optJSONObject(index) ?: continue
                val track = wrapper.optJSONObject("item") ?: wrapper.optJSONObject("track") ?: continue
                val title = track.optString("name").trim()
                if (title.isEmpty()) continue
                val artistsJson = track.optJSONArray("artists") ?: JSONArray()
                val artists = buildList {
                    for (artistIndex in 0 until artistsJson.length()) {
                        artistsJson.optJSONObject(artistIndex)?.optString("name")
                            ?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }.joinToString(", ")
                val album = track.optJSONObject("album")
                val id = track.optString("id").ifBlank { track.optString("uri") }
                tracks += SpotifyAccountTrack(
                    key = "$id:${offset + index}",
                    title = title,
                    artists = artists,
                    album = album?.optString("name").orEmpty(),
                    albumArtUrl = album?.optJSONArray("images")?.optJSONObject(0)
                        ?.optString("url")?.takeIf(String::isNotBlank),
                    durationMs = track.optLong("duration_ms", 0L),
                )
            }
            if (items.length() == 0) break
            offset += items.length()
        }
        if (tracks.isEmpty()) error("This Spotify playlist has no importable songs.")
        SpotifyAccountPlaylist(
            id = playlistId,
            name = metadata.optString("name").ifBlank { "Spotify Playlist" },
            coverUrl = metadata.optJSONArray("images")?.optJSONObject(0)
                ?.optString("url")?.takeIf(String::isNotBlank),
            tracks = tracks,
        )
    }

    suspend fun getTopTracksForRecommendations(): List<SpotifyAccountTrack> = withContext(Dispatchers.IO) {
        if (!hasUsableSession()) return@withContext emptyList()
        runCatching {
            getPlaylistForImport("spotify_top_tracks_short_term").tracks
        }.getOrElse {
            runCatching {
                getPlaylistForImport("spotify_saved_liked_songs").tracks.take(25)
            }.getOrDefault(emptyList())
        }
    }

    fun logout() {
        preferences.edit().clear().apply()
        _isLoggedIn.value = false
        _accountName.value = ""
        _tasteProfile.value = SpotifyTasteProfile()
    }

    private fun authorizedGet(path: String): JSONObject {
        val accessToken = validAccessToken()
        val response = execute(
            Request.Builder()
                .url("$API_BASE$path")
                .header("Authorization", "Bearer $accessToken")
                .build()
        )
        return JSONObject(response)
    }

    private fun optionalAuthorizedGet(path: String): JSONObject? = runCatching {
        authorizedGet(path)
    }.onFailure { timber.log.Timber.w(it, "Optional Spotify endpoint unavailable: %s", path) }
        .getOrNull()

    private fun validAccessToken(): String {
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        val currentToken = preferences.getString(KEY_ACCESS_TOKEN, null)
        if (!currentToken.isNullOrBlank() && System.currentTimeMillis() < expiresAt - 60_000L) {
            return currentToken
        }

        val spDc = preferences.getString(KEY_SP_DC, null)
        if (!spDc.isNullOrBlank()) {
            val refreshed = fetchWebPlayerToken(spDc)
            val accessToken = refreshed.getString("accessToken")
            val expMs = refreshed.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 3600_000L)
            preferences.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_EXPIRES_AT, expMs)
                .apply()
            return accessToken
        }

        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)
        if (!refreshToken.isNullOrBlank() && BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()) {
            val refreshed = exchangeToken(
                FormBody.Builder()
                    .add("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build()
            )
            persistToken(refreshed, refreshToken)
            return refreshed.getString("access_token")
        }

        error("Spotify session expired. Please sign in again.")
    }

    private fun exchangeToken(body: FormBody): JSONObject = JSONObject(
        execute(Request.Builder().url(TOKEN_URL).post(body).build())
    )

    private fun persistToken(token: JSONObject, existingRefreshToken: String? = null) {
        val accessToken = token.getString("access_token")
        val refreshToken = token.optString("refresh_token").ifBlank { existingRefreshToken.orEmpty() }
        val expiresAt = System.currentTimeMillis() + token.optLong("expires_in", 3600L) * 1000L
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    private fun execute(request: Request): String = httpClient.newCall(request).execute().use { response ->
        val payload = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val apiMessage = runCatching {
                JSONObject(payload).optJSONObject("error")?.optString("message")
            }.getOrNull().orEmpty()
            val guidance = when (response.code) {
                401 -> "Spotify session expired. Sign in again."
                403 -> "Spotify denied this request. In Development Mode the account must be added in the Spotify dashboard and the app owner must have Spotify Premium. Followed playlists can expose songs only when the user owns or collaborates on them."
                429 -> "Spotify rate limit reached. Please wait and retry."
                else -> "Spotify request failed (${response.code})."
            }
            error(listOf(guidance, apiMessage).filter { it.isNotBlank() }.distinct().joinToString(" "))
        }
        payload
    }

    private fun hasUsableSession(): Boolean {
        val hasSpDc = !preferences.getString(KEY_SP_DC, null).isNullOrBlank()
        val hasOAuth = !preferences.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank() && BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()
        return hasSpDc || hasOAuth
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        val REDIRECT_URI: String get() = BuildConfig.SPOTIFY_REDIRECT_URI
        private const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val API_BASE = "https://api.spotify.com/v1"
        private const val PLAYLIST_PAGE_SIZE = 50
        private const val TRACK_PAGE_SIZE = 50
        private const val SCOPES = "playlist-read-private playlist-read-collaborative user-library-read user-top-read user-read-private"
        private const val PREFS_NAME = "spotify_account_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_SP_DC = "sp_dc"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_PENDING_VERIFIER = "pending_verifier"
        private const val KEY_PENDING_STATE = "pending_state"
    }
}
