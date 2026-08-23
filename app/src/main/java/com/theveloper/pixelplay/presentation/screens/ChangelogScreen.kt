package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private data class ChangelogRelease(
    val version: String,
    val date: String,
    val changes: List<String>,
)

private val VybeChangelog = listOf(
    ChangelogRelease(
        version = "0.7.11",
        date = "23 August 2026",
        changes = listOf(
            "Made artist pages render immediately and progressively load original profiles, artwork, and complete catalogues.",
            "Added a Storage & cache dashboard for artwork cache, offline downloads, other app data, and total usage.",
            "Added VYBE song links that open and play shared tracks in VYBE, with a download fallback for recipients.",
            "Fixed missing online durations and hid private online and in-app download paths from Song Info.",
            "Hardened database and transient-cache recovery during updates from older VYBE versions.",
        ),
    ),
    ChangelogRelease(
        version = "0.7.10",
        date = "22 August 2026",
        changes = listOf(
            "Prevented legacy or damaged local databases from trapping VYBE in a startup crash loop.",
            "Added automatic recovery for corrupted app settings while preserving healthy user data.",
        ),
    ),
    ChangelogRelease(
        version = "0.7.8",
        date = "22 August 2026",
        changes = listOf(
            "Enabled Go to Album and Go to Artist for online search and Home song menus.",
            "Added on-demand YouTube Music album lookup when a result lacks its album browse ID.",
            "Kept music audio results while filtering podcast and episode content from search.",
            "Made real download-progress and completion notifications silent.",
        ),
    ),
    ChangelogRelease(
        version = "0.7.7",
        date = "22 August 2026",
        changes = listOf(
            "Added live download progress in the app and Android notifications, including failed-download retry feedback.",
            "Added list/grid switching to Downloads, Songs, Albums, Artists, Playlists, and Liked.",
            "Filled empty Library categories from listening history while keeping device folders unchanged.",
            "Filtered podcast episodes from music search and added artist cards plus expanded artist catalogues.",
            "Added album and artist navigation from Now Playing, including a picker for collaborations.",
            "Improved landscape YouTube Music artwork with a square fitted image and blurred background fill.",
        ),
    ),
    ChangelogRelease(
        version = "0.7.6",
        date = "22 August 2026",
        changes = listOf(
            "Added in-app GitHub release update checks, APK downloads, progress, and installer handoff.",
            "Added volume-up resume after VYBE automatically pauses at zero media volume.",
            "Fixed online artist navigation by preserving YouTube Music browse IDs.",
            "Added a visible and playable Top Songs section to online artist profiles.",
            "Improved synchronized lyric scrolling so it no longer visually lags by one or two seconds.",
            "Kept Spotify public-URL import, removed account sign-in, improved parsing, and added large-playlist progress.",
            "Changed the scrollbar default to off; it can still be enabled in Settings.",
        ),
    ),
    ChangelogRelease(
        version = "0.7.5",
        date = "13 June 2026",
        changes = listOf(
            "VYBE playback, search, library, downloads, lyrics, accounts, and personalization baseline.",
            "Added YouTube Music integration, AI tools, Wear OS support, and extensive performance improvements.",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBackClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Changelog", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(VybeChangelog, key = { it.version }) { release ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(24.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "VYBE ${release.version}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = release.date,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        release.changes.forEach { change ->
                            Text(
                                text = "• $change",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
