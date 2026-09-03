package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository // Importar MusicRepository
import com.theveloper.pixelplay.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val onlineMusicRepository: com.theveloper.pixelplay.data.repository.OnlineMusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        val rawAlbumId: String? = savedStateHandle.get("albumId")
        val albumIdString: String? = rawAlbumId?.let { android.net.Uri.decode(it) }?.trim()
        if (!albumIdString.isNullOrBlank()) {
            when {
                albumIdString.startsWith(REMOTE_ALBUM_PREFIX) ->
                    loadOnlineAlbumData(albumIdString.removePrefix(REMOTE_ALBUM_PREFIX))
                albumIdString.startsWith(LOOKUP_ALBUM_PREFIX) -> {
                    val parts = albumIdString.removePrefix(LOOKUP_ALBUM_PREFIX).split('|', limit = 2)
                    loadOnlineAlbumByMetadata(parts.firstOrNull().orEmpty(), parts.getOrNull(1).orEmpty())
                }
                albumIdString.startsWith(COMPOSITE_ALBUM_PREFIX) -> {
                    val parts = albumIdString.removePrefix(COMPOSITE_ALBUM_PREFIX).split('|')
                    val id = parts.getOrNull(0)?.toLongOrNull()
                    val title = parts.getOrNull(1).orEmpty()
                    val artist = parts.getOrNull(2).orEmpty()
                    if (id != null) {
                        loadAlbumData(id, fallbackTitle = title, fallbackArtist = artist)
                    } else {
                        loadOnlineAlbumByMetadata(title, artist)
                    }
                }
                albumIdString.toLongOrNull() != null -> loadAlbumData(albumIdString.toLong())
                else -> loadOnlineAlbumData(albumIdString)
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.album_detail_id_not_found), isLoading = false) }
        }
    }

    private fun loadOnlineAlbumByMetadata(title: String, artist: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val match = runCatching {
                onlineMusicRepository.searchMusicStructured("$title $artist").albums
                    .firstOrNull { album ->
                        album.title.equals(title, ignoreCase = true) &&
                            (artist.isBlank() || album.artist.contains(artist, ignoreCase = true) ||
                                artist.contains(album.artist, ignoreCase = true))
                    }
                    ?: onlineMusicRepository.searchMusicStructured(title).albums
                        .firstOrNull { it.title.equals(title, ignoreCase = true) }
            }.getOrNull()
            if (match != null) {
                loadOnlineAlbumData(match.browseId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.album_detail_not_found), isLoading = false) }
            }
        }
    }

    private fun loadOnlineAlbumData(browseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val directDetails = runCatching { onlineMusicRepository.getAlbumDetails(browseId) }.getOrNull()
                val isRawId = browseId.startsWith("MPREb_") || browseId.startsWith("OLAK5uy_")
                val matchedAlbum = if (directDetails == null && !isRawId) {
                    onlineMusicRepository.searchMusicStructured(browseId).albums
                        .firstOrNull { it.title.equals(browseId, ignoreCase = true) }
                } else null
                val resolvedBrowseId = matchedAlbum?.browseId ?: browseId
                val details = directDetails
                    ?: matchedAlbum?.let { onlineMusicRepository.getAlbumDetails(it.browseId) }
                if (details != null) {
                    val resolvedCover = details.coverUrl
                        ?: details.tracks.firstNotNullOfOrNull { it.albumArtUriString?.takeIf(String::isNotBlank) }
                    val resolvedTitle = details.title.takeUnless { it.equals("Album", ignoreCase = true) }
                        ?: details.tracks.firstNotNullOfOrNull { track ->
                            track.album.takeUnless { it.isBlank() || it.equals("YouTube Music", ignoreCase = true) }
                        }
                        ?: details.title
                    val resolvedArtist = details.artist.takeUnless { it.equals("Various Artists", ignoreCase = true) }
                        ?: details.tracks.firstOrNull()?.artist
                        ?: details.artist
                    val resolvedTracks = details.tracks.map { track ->
                        track.copy(
                            album = resolvedTitle,
                            albumArtUriString = track.albumArtUriString?.takeIf(String::isNotBlank) ?: resolvedCover,
                        )
                    }
                    val album = Album(
                        id = resolvedBrowseId.hashCode().toLong(),
                        title = resolvedTitle,
                        artist = resolvedArtist,
                        year = details.year ?: 0,
                        dateAdded = System.currentTimeMillis(),
                        albumArtUriString = resolvedCover,
                        songCount = resolvedTracks.size,
                        albumArtist = resolvedArtist,
                        remoteBrowseId = resolvedBrowseId,
                    )
                    _uiState.value = AlbumDetailUiState(
                        album = album,
                        songs = resolvedTracks,
                        isLoading = false
                    )
                } else {
                    _uiState.update { it.copy(error = context.getString(R.string.album_detail_not_found), isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to load album", isLoading = false) }
            }
        }
    }

    private fun loadAlbumData(id: Long, fallbackTitle: String = "", fallbackArtist: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val albumDetailsFlow = musicRepository.getAlbumById(id)
                val albumSongsFlow = musicRepository.getSongsForAlbum(id)

                combine(albumDetailsFlow, albumSongsFlow) { album, songs ->
                    val resolvedAlbum = album ?: songs.firstOrNull()?.let { firstSong ->
                        val title = firstSong.album.takeUnless {
                            it.isBlank() || it.equals("YouTube Music", ignoreCase = true)
                        } ?: firstSong.title
                        Album(
                            id = id,
                            title = title,
                            artist = firstSong.albumArtist?.takeIf(String::isNotBlank) ?: firstSong.artist,
                            year = firstSong.year,
                            dateAdded = songs.maxOfOrNull { it.dateAdded } ?: firstSong.dateAdded,
                            albumArtUriString = songs.firstNotNullOfOrNull {
                                it.albumArtUriString?.takeIf(String::isNotBlank)
                            },
                            songCount = songs.size,
                            albumArtist = firstSong.albumArtist?.takeIf(String::isNotBlank) ?: firstSong.artist,
                            remoteBrowseId = firstSong.remoteAlbumBrowseId,
                        )
                    }
                    if (resolvedAlbum != null && songs.isNotEmpty()) {
                        AlbumDetailUiState(
                            album = resolvedAlbum,
                            songs = songs.sortedWith(
                                compareBy<Song> { it.discNumber ?: 1 }
                                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                    .thenBy { it.title.lowercase() }
                            ),
                            isLoading = false
                        )
                    } else if (fallbackTitle.isNotBlank()) {
                        // Fall back to online lookup
                        null
                    } else if (resolvedAlbum != null) {
                        AlbumDetailUiState(
                            album = resolvedAlbum,
                            songs = emptyList(),
                            isLoading = false
                        )
                    } else {
                        AlbumDetailUiState(
                            error = context.getString(R.string.album_detail_not_found),
                            isLoading = false
                        )
                    }
                }
                    .catch { e ->
                        emit(
                            AlbumDetailUiState(
                                error = context.getString(R.string.album_detail_error_loading_album, e.localizedMessage ?: ""),
                                isLoading = false
                            )
                        )
                    }
                    .collect { newState ->
                        if (newState == null && fallbackTitle.isNotBlank()) {
                            loadOnlineAlbumByMetadata(fallbackTitle, fallbackArtist)
                        } else if (newState != null) {
                            _uiState.value = newState
                        }
                    }

            } catch (e: Exception) {
                if (fallbackTitle.isNotBlank()) {
                    loadOnlineAlbumByMetadata(fallbackTitle, fallbackArtist)
                } else {
                    _uiState.update {
                        it.copy(
                            error = context.getString(R.string.album_detail_error_loading_album, e.localizedMessage ?: ""),
                            isLoading = false
                        )
                    }
                }
            }
        }
    }

    fun update(songs: List<Song>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                songs = songs
            )
        }
    }

    private companion object {
        const val REMOTE_ALBUM_PREFIX = "remote_album|"
        const val LOOKUP_ALBUM_PREFIX = "lookup_album|"
        const val COMPOSITE_ALBUM_PREFIX = "album_meta|"
    }
}
