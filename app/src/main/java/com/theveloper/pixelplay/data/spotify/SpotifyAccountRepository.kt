package com.theveloper.pixelplay.data.spotify

import android.content.Context
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

/** Official Spotify Web API + OAuth PKCE account integration. */
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
    } catch (e: Exception) {
        timber.log.Timber.e(e, "SpotifyAccountRepository: Failed to create EncryptedSharedPreferences. Clearing corrupted file.")
        val dir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
        val file = java.io.File(dir, "$PREFS_NAME.xml")
        if (file.exists()) file.delete()
        
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _isLoggedIn = MutableStateFlow(hasUsableSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    private val _accountName = MutableStateFlow(preferences.getString(KEY_ACCOUNT_NAME, null).orEmpty())
    val accountName: StateFlow<String> = _accountName.asStateFlow()
    private val _tasteProfile = MutableStateFlow(SpotifyTasteProfile())
    val tasteProfile: StateFlow<SpotifyTasteProfile> = _tasteProfile.asStateFlow()

    val isConfigured: Boolean get() = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()

    fun createAuthorizationUri(): Uri {
        check(isConfigured) { "Add SPOTIFY_CLIENT_ID to local.properties for Spotify sign-in." }
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
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPES)
            .build()
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
                    .add("redirect_uri", REDIRECT_URI)
                    .add("code_verifier", verifier)
                    .build()
            )
            persistToken(token)
            preferences.edit().remove(KEY_PENDING_STATE).remove(KEY_PENDING_VERIFIER).apply()
            refreshProfileAndTaste()
            _isLoggedIn.value = true
        }.onFailure { _isLoggedIn.value = false }
    }

    suspend fun refreshProfileAndTaste(): SpotifyTasteProfile = withContext(Dispatchers.IO) {
        val profile = authorizedGet("/me")
        val displayName = profile.optString("display_name").ifBlank { profile.optString("id", "Spotify User") }
        val playlists = authorizedGet("/me/playlists?limit=1").optInt("total")
        val saved = authorizedGet("/me/tracks?limit=1").optInt("total")
        val topTracks = authorizedGet("/me/top/tracks?limit=50&time_range=medium_term")
            .optJSONArray("items")?.length() ?: 0
        val topArtists = authorizedGet("/me/top/artists?limit=50&time_range=medium_term")
            .optJSONArray("items")?.length() ?: 0
        val taste = SpotifyTasteProfile(playlists, saved, topTracks, topArtists)
        preferences.edit().putString(KEY_ACCOUNT_NAME, displayName).apply()
        _accountName.value = displayName
        _tasteProfile.value = taste
        _isLoggedIn.value = true
        taste
    }

    suspend fun getCurrentUserPlaylists(): List<SpotifyRemotePlaylist> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyRemotePlaylist>()
        var offset = 0
        do {
            val page = authorizedGet("/me/playlists?limit=$PLAYLIST_PAGE_SIZE&offset=$offset")
            val items = page.optJSONArray("items") ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isEmpty()) continue
                result += SpotifyRemotePlaylist(
                    id = id,
                    name = item.optString("name").ifBlank { "Spotify Playlist" },
                    coverUrl = item.optJSONArray("images")?.optJSONObject(0)
                        ?.optString("url")?.takeIf(String::isNotBlank),
                    trackCount = item.optJSONObject("items")?.optInt("total")
                        ?: item.optJSONObject("tracks")?.optInt("total")
                        ?: 0,
                    ownerName = item.optJSONObject("owner")?.optString("display_name")
                        ?.ifBlank { item.optJSONObject("owner")?.optString("id") }
                        .orEmpty(),
                )
            }
            offset += items.length()
        } while (items.length() > 0 && offset < page.optInt("total", offset))
        result.distinctBy { it.id }
    }

    suspend fun getPlaylistForImport(playlistId: String): SpotifyAccountPlaylist = withContext(Dispatchers.IO) {
        require(playlistId.matches(Regex("[A-Za-z0-9]+"))) { "Invalid Spotify playlist ID" }
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
                if (track.optString("type", "track") != "track") continue
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

    private fun validAccessToken(): String {
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        if (System.currentTimeMillis() < expiresAt - 60_000L) {
            return preferences.getString(KEY_ACCESS_TOKEN, null)
                ?: error("Spotify session is missing.")
        }
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)
            ?: error("Spotify session expired. Please sign in again.")
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
        if (!response.isSuccessful) error("Spotify request failed (${response.code})")
        payload
    }

    private fun hasUsableSession(): Boolean =
        !preferences.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank() && isConfigured

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val REDIRECT_URI = "pixelplayer://spotify-callback"
        private const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val API_BASE = "https://api.spotify.com/v1"
        private const val PLAYLIST_PAGE_SIZE = 50
        private const val TRACK_PAGE_SIZE = 50
        private const val SCOPES = "playlist-read-private user-library-read user-top-read user-read-private"
        private const val PREFS_NAME = "spotify_account_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_PENDING_VERIFIER = "pending_verifier"
        private const val KEY_PENDING_STATE = "pending_state"
    }
}
