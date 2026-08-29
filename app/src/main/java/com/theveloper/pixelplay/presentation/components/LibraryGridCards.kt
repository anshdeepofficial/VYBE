package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySongGridCard(
    song: Song,
    onClick: () -> Unit,
    progress: Int? = null,
    progressLabel: String? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier)
            .combinedClickable(
                onClick = { if (isSelectionMode) onSelectionToggle() else onClick() },
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column {
            AsyncImage(
                model = song.albumArtUriString,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = progressLabel ?: song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                progress?.let {
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { it.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryArtistGridCard(artist: Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = artist.effectiveImageUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(132.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${artist.songCount} songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LibraryPlaylistGridCard(playlist: Playlist, onClick: () -> Unit) {
    LibrarySongGridCard(
        song = Song.emptySong().copy(
            id = playlist.id,
            title = playlist.name,
            artist = "${playlist.songIds.size} songs",
            albumArtUriString = playlist.coverImageUri,
        ),
        onClick = onClick,
    )
}
