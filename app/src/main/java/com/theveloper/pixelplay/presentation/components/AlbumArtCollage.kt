package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.utils.MediaItemBuilder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers

// Kept for the legacy pattern-config helpers used by previews and older call sites.
@Stable
data class Config(
    val size: Dp,
    val width: Dp,
    val height: Dp,
    val align: Alignment,
    val rot: Float,
    val shape: Shape,
    val offsetX: Dp,
    val offsetY: Dp,
)

/**
 * Muestra hasta 6 portadas en un layout de collage con formas simplificadas y redondeadas.
 * Las formas se dividen en dos grupos (superior e inferior) para evitar superposición.
 * Incluye una píldora central, círculo, squircle y estrella, con disposición ajustada.
 * Ajusta tamaños, rotaciones y posiciones para crear un look dinámico.
 * Utiliza BoxWithConstraints para adaptar las dimensiones al contenedor.
 */
@Composable
fun AlbumArtCollage(
    songs: ImmutableList<Song>,
    modifier: Modifier = Modifier,
    height: Dp = 400.dp,
    padding: Dp = 0.dp,
    pattern: CollagePattern = CollagePattern.default,
    onSongClick: (Song) -> Unit,
) {
    val context = LocalContext.current
    val songsToShow = remember(songs) {
        (songs.take(6) + List(6 - songs.size.coerceAtMost(6)) { null }).toImmutableList()
    }

    val requests = remember(songsToShow, context) {
        songsToShow.map { song ->
            song?.albumArtUriString?.let {
                ImageRequest.Builder(context)
                    .data(MediaItemBuilder.highResolutionArtworkUrl(it))
                    .dispatcher(Dispatchers.IO)
                    .size(1200)
                    .crossfade(true)
                    .error(R.drawable.ic_music_placeholder)
                    .build()
            }
        }.toImmutableList()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(padding)
    ) {
        if (songs.isNotEmpty()) {
            val order = remember(pattern) {
                when (pattern) {
                    CollagePattern.COSMIC_SWIRL -> listOf(0, 1, 2, 3, 4, 5)
                    CollagePattern.HONEYCOMB_GROOVE -> listOf(1, 0, 2, 4, 3, 5)
                    CollagePattern.VINYL_STACK -> listOf(2, 1, 0, 5, 4, 3)
                    CollagePattern.PIXEL_MOSAIC -> listOf(3, 0, 4, 1, 5, 2)
                    CollagePattern.STARDUST_SCATTER -> listOf(4, 2, 0, 5, 1, 3)
                }
            }
            val corner = when (pattern) {
                CollagePattern.HONEYCOMB_GROOVE -> 28.dp
                CollagePattern.VINYL_STACK -> 40.dp
                CollagePattern.PIXEL_MOSAIC -> 14.dp
                CollagePattern.STARDUST_SCATTER -> 32.dp
                else -> 22.dp
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().weight(1.16f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CollageArtworkTile(order[0], songsToShow, requests, corner, Modifier.weight(1.6f).fillMaxHeight(), onSongClick)
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CollageArtworkTile(order[1], songsToShow, requests, corner, Modifier.weight(1f).fillMaxWidth(), onSongClick)
                        CollageArtworkTile(order[2], songsToShow, requests, corner, Modifier.weight(1f).fillMaxWidth(), onSongClick)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().weight(0.84f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    order.drop(3).forEach { index ->
                        CollageArtworkTile(index, songsToShow, requests, corner, Modifier.weight(1f).fillMaxHeight(), onSongClick)
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.rounded_music_note_24),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun CollageArtworkTile(
    index: Int,
    songs: ImmutableList<Song?>,
    requests: ImmutableList<ImageRequest?>,
    corner: Dp,
    modifier: Modifier,
    onSongClick: (Song) -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val song = songs.getOrNull(index)
    val request = requests.getOrNull(index)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (song != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSongClick(song) }
                ) else Modifier
            )
    ) {
        if (song != null && request != null) {
            // A soft edge-to-edge backdrop fills every bento cell, while the sharp foreground
            // uses Fit so square and 16:9 artwork is always visible in full.
            SmartImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(16.dp)
            )
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.16f)))
            SmartImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.matchParentSize().padding(3.dp).clip(RoundedCornerShape((corner - 3.dp).coerceAtLeast(8.dp)))
            )
        }
    }
}
