package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@UnstableApi
@Composable
fun InlineLyricsCoverView(
    song: Song,
    playerViewModel: PlayerViewModel,
    currentPositionProvider: () -> Long,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val lyrics = stableState.lyrics
    val isLoadingLyrics = stableState.isLoadingLyrics
    val syncOffset by playerViewModel.currentSongLyricsSyncOffset.collectAsStateWithLifecycle()

    val currentPosition = currentPositionProvider() + syncOffset

    val containerColor = LocalMaterialTheme.current.surfaceContainerHigh.copy(alpha = 0.92f)
    val onContainerColor = LocalMaterialTheme.current.onSurface
    val primaryColor = LocalMaterialTheme.current.primary

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp)),
        color = containerColor,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Lyrics",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded,
                                color = onContainerColor
                            )
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(LocalMaterialTheme.current.surfaceContainerHighest.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Lyrics",
                            tint = onContainerColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Content
                val syncedLines = lyrics?.synced
                val plainLines = lyrics?.plain

                when {
                    isLoadingLyrics -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = primaryColor,
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Loading lyrics…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onContainerColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    !syncedLines.isNullOrEmpty() -> {
                        val listState = rememberLazyListState()
                        val activeLineIndex by remember(syncedLines, currentPosition) {
                            derivedStateOf {
                                val idx = syncedLines.indexOfLast { it.time <= currentPosition }
                                if (idx >= 0) idx else 0
                            }
                        }

                        LaunchedEffect(activeLineIndex) {
                            if (activeLineIndex in syncedLines.indices) {
                                listState.animateScrollToItem(
                                    index = (activeLineIndex - 1).coerceAtLeast(0)
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = syncedLines,
                                key = { index, line -> "$index-${line.time}" }
                            ) { index, line ->
                                val isActive = index == activeLineIndex
                                val textColor = if (isActive) primaryColor else onContainerColor.copy(alpha = 0.5f)

                                Text(
                                    text = line.line,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = if (isActive) 20.sp else 16.sp,
                                        fontFamily = GoogleSansRounded,
                                        color = textColor
                                    ),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            playerViewModel.seekTo(line.time.toLong())
                                        }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                    !plainLines.isNullOrEmpty() -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(plainLines) { _, lineText ->
                                Text(
                                    text = lineText,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = GoogleSansRounded,
                                        color = onContainerColor.copy(alpha = 0.85f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Lyrics unavailable",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = GoogleSansRounded,
                                        color = onContainerColor.copy(alpha = 0.7f)
                                    )
                                )
                                Text(
                                    text = "Tap to return to artwork",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onContainerColor.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
