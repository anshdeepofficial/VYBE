package com.theveloper.pixelplay.presentation.components.player

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.sharing.VybeSongShareLink

sealed interface OptionIcon {
    data class DrawableRes(val resId: Int) : OptionIcon
    data class Vector(val vector: ImageVector) : OptionIcon
}

data class NowPlayingOptionItem(
    val icon: OptionIcon,
    val title: String,
    val subtitle: String? = null,
    val isActive: Boolean = false,
    val activeColor: Color? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingOptionsBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onArtists: () -> Unit,
    onAlbum: () -> Unit,
    onLyricsProvider: () -> Unit,
    onSleepTimer: () -> Unit,
    onPlaybackSpeed: () -> Unit
) {
    val context = LocalContext.current
    val isStreaming = song.path.startsWith("http") || song.contentUriString.startsWith("http")

    val options = listOf(
        // 1. Like / Unlike
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(
                if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24
            ),
            title = if (isFavorite) "Liked / In Favorites" else "Like this song",
            subtitle = if (isFavorite) "Tap to remove from favorites" else "Add to your liked songs",
            isActive = isFavorite,
            activeColor = MaterialTheme.colorScheme.primary,
            onClick = {
                onFavoriteToggle()
                onDismiss()
            }
        ),

        // 2. Download
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(R.drawable.rounded_download_24),
            title = if (!isStreaming && !song.id.startsWith("yt_") && !song.id.startsWith("audius_")) "Downloaded" else "Download",
            subtitle = if (!isStreaming && !song.id.startsWith("yt_") && !song.id.startsWith("audius_")) "Available offline on device" else "Save for offline playback",
            onClick = {
                if (isStreaming || song.id.startsWith("yt_") || song.id.startsWith("audius_") || song.path.startsWith("http")) {
                    com.theveloper.pixelplay.data.network.ytmusic.YouTubeDownloadManager
                        .fromContext(context)
                        .enqueueDownload(song)
                } else {
                    Toast.makeText(context, "Song is already available on your device", Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }
        ),

        // 3. Share
        NowPlayingOptionItem(
            icon = OptionIcon.Vector(Icons.Default.Share),
            title = "Share",
            subtitle = "Share a short VYBE song link",
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "${song.title} on VYBE")
                    putExtra(Intent.EXTRA_TEXT, VybeSongShareLink.shareText(song))
                }
                context.startActivity(Intent.createChooser(intent, "Share via"))
                onDismiss()
            }
        ),

        // 4. Add to a playlist
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(R.drawable.rounded_playlist_add_24),
            title = "Add to a playlist",
            subtitle = "Save to your playlists",
            onClick = {
                onDismiss()
                onAddToPlaylist()
            }
        ),

        // 4. Play next
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(R.drawable.rounded_playlist_play_24),
            title = "Play next",
            subtitle = "Insert after current song in queue",
            onClick = {
                onPlayNext()
                Toast.makeText(context, "Playing next: ${song.title}", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        ),

        // 5. Add to queue
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(R.drawable.rounded_queue_music_24),
            title = "Add to queue",
            subtitle = "Append to the end of playback queue",
            onClick = {
                onAddToQueue()
                Toast.makeText(context, "Added to queue: ${song.title}", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        ),

        // 6. Artists
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(R.drawable.rounded_artist_24),
            title = "Artists",
            subtitle = song.displayArtist.ifBlank { "View artist details" },
            onClick = {
                onDismiss()
                onArtists()
            }
        ),

        // 7. Album
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(R.drawable.rounded_album_24),
            title = "Album",
            subtitle = song.album.ifBlank { "View album tracks" },
            onClick = {
                onDismiss()
                onAlbum()
            }
        ),

        // 8. Main Lyrics Provider
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(R.drawable.rounded_lyrics_24),
            title = "Main Lyrics Provider",
            subtitle = "Fetch / switch lyrics source",
            onClick = {
                onDismiss()
                onLyricsProvider()
            }
        ),

        // 9. Sleep Timer
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(R.drawable.rounded_timer_24),
            title = "Sleep Timer",
            subtitle = "Stop playback automatically after a timer",
            onClick = {
                onDismiss()
                onSleepTimer()
            }
        ),

        // 10. Playback speed & pitch
        NowPlayingOptionItem(
            icon = OptionIcon.DrawableRes(R.drawable.rounded_touch_app_24),
            title = "Playback speed & pitch",
            subtitle = "Adjust tempo and audio frequency",
            onClick = {
                onDismiss()
                onPlaybackSpeed()
            }
        ),


    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // ── Top Header Section ──────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    AsyncImage(
                        model = song.albumArtUriString,
                        contentDescription = song.title,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ── Action Options List ─────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(options.size) { index ->
                    val option = options[index]
                    ListItem(
                        headlineContent = {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (option.isActive) FontWeight.Bold else FontWeight.Medium,
                                color = option.activeColor ?: MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = option.subtitle?.let {
                            {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingContent = {
                            when (val icon = option.icon) {
                                is OptionIcon.DrawableRes -> Icon(
                                    painter = painterResource(icon.resId),
                                    contentDescription = option.title,
                                    tint = option.activeColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                is OptionIcon.Vector -> Icon(
                                    imageVector = icon.vector,
                                    contentDescription = option.title,
                                    tint = option.activeColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        modifier = Modifier.clickable { option.onClick() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
