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
        val albumIdString: String? = savedStateHandle.get("albumId")
        if (albumIdString != null) {
            val albumId = albumIdString.toLongOrNull()
            if (albumId != null) {
                loadAlbumData(albumId)
            } else {
                loadOnlineAlbumData(albumIdString)
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.album_detail_id_not_found), isLoading = false) }
        }
    }

    private fun loadOnlineAlbumData(browseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val directDetails = runCatching { onlineMusicRepository.getAlbumDetails(browseId) }.getOrNull()
                val matchedAlbum = if (directDetails == null) {
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
                    _uiState.update { it.copy(error = "Album not found", isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to load album", isLoading = false) }
            }
        }
    }

    private fun loadAlbumData(id: Long) {
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
                    if (resolvedAlbum != null) {
                        AlbumDetailUiState(
                            album = resolvedAlbum,
                            songs = songs.sortedWith(
                                compareBy<Song> { it.discNumber ?: 1 }
                                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                    .thenBy { it.title.lowercase() }
                            ),
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
                        _uiState.value = newState
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.album_detail_error_loading_album, e.localizedMessage ?: ""),
                        isLoading = false
                    )
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
}
