package com.theveloper.pixelplay.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.theveloper.pixelplay.presentation.viewmodel.OnlineSearchViewModel
import com.theveloper.pixelplay.presentation.viewmodel.OnlineSearchFilter
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.SongContextBottomSheet
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeAlbum
import com.theveloper.pixelplay.data.network.ytmusic.YouTubeArtist
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    paddingValuesParent: PaddingValues,
    viewModel: OnlineSearchViewModel = hiltViewModel()
) {
    val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
    val aiRecommendations by viewModel.aiRecommendations.collectAsStateWithLifecycle()
    val searchResultsSongs by viewModel.searchResultsSongs.collectAsStateWithLifecycle()
    val searchResultsAlbums by viewModel.searchResultsAlbums.collectAsStateWithLifecycle()
    val searchResultsArtists by viewModel.searchResultsArtists.collectAsStateWithLifecycle()
    val searchFilter by viewModel.searchFilter.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val isYouTubeMusicConnected by viewModel.isYouTubeMusicConnected.collectAsStateWithLifecycle()
    val accountInterestLabels by viewModel.accountInterestLabels.collectAsStateWithLifecycle()
    val discoveryTitle by viewModel.discoveryTitle.collectAsStateWithLifecycle()
    val discoveryArtists by viewModel.discoveryArtists.collectAsStateWithLifecycle()
    val querySuggestions by viewModel.querySuggestions.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var selectedGenre by rememberSaveable { mutableStateOf<String?>(null) }

    // Context menu state
    val contextMenuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var contextMenuSong by remember { mutableStateOf<Song?>(null) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val searchInputFocusRequester = remember { FocusRequester() }
    val bottomPadding = paddingValuesParent.calculateBottomPadding() + MiniPlayerHeight + 16.dp

    val isSearching = query.isNotBlank()
    val discoveryChips = remember(isYouTubeMusicConnected, accountInterestLabels) {
        buildList {
            add("Trending")
            add("Latest Releases")
            if (isYouTubeMusicConnected) addAll(accountInterestLabels)
        }.distinctBy { it.lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(paddingValuesParent.calculateTopPadding() + 16.dp))

        // ── Search Bar ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                DockedSearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            modifier = Modifier.focusRequester(searchInputFocusRequester),
                            query = query,
                            onQueryChange = {
                                query = it
                                selectedGenre = null
                                if (it.isNotBlank()) viewModel.search(it)
                                else viewModel.clearSearch()
                            },
                            onSearch = { if (query.isNotBlank()) viewModel.submitSearch(query) },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text("Search songs, movies, albums, artists") },
                            colors = SearchBarDefaults.inputFieldColors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = SearchBarDefaults.colors(
                        containerColor = Color.Transparent,
                        dividerColor = Color.Transparent
                    ),
                    content = {}
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (query.isNotBlank() && querySuggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(querySuggestions, key = { "suggestion_$it" }) { suggestion ->
                    AssistChip(
                        onClick = {
                            query = suggestion
                            selectedGenre = null
                            viewModel.submitSearch(suggestion)
                        },
                        label = { Text(suggestion, maxLines = 1) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (query.isBlank() && recentSearches.isNotEmpty()) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recentSearches, key = { "recent_${it.id}_${it.query}" }) { history ->
                    InputChip(
                        selected = false,
                        onClick = {
                            query = history.query
                            selectedGenre = null
                            viewModel.submitSearch(history.query)
                        },
                        label = { Text(history.query, maxLines = 1) },
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.deleteRecentSearch(history.query) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove ${history.query} from search history",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Chips Bar ───────────────────────────────────────────────────
        if (isSearching) {
            // Category filter chips: All, Songs, Albums, Artists
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnlineSearchFilter.values().forEach { filter ->
                    FilterChip(
                        selected = searchFilter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(filter.name.lowercase().capitalize()) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        } else {
            // Genre recommendation chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(discoveryChips, key = { "discovery_$it" }) { genre ->
                    val isSelected = genre == selectedGenre
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                selectedGenre = genre
                                query = ""
                                viewModel.clearSearch()
                                viewModel.selectDiscovery(genre)
                            }
                        },
                        label = { Text(genre) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Content ─────────────────────────────────────────────────────
        val hasVisibleSearchResults = searchResultsSongs.isNotEmpty() ||
            searchResultsAlbums.isNotEmpty() || searchResultsArtists.isNotEmpty()
        val hasVisibleDiscovery = trendingTracks.isNotEmpty() || aiRecommendations.isNotEmpty() || discoveryArtists.isNotEmpty()
        if (isLoading && ((isSearching && !hasVisibleSearchResults) || (!isSearching && !hasVisibleDiscovery))) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (isSearching) {
            // ── Structured Search Results View ──────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Songs Section
                if (searchFilter == OnlineSearchFilter.ALL || searchFilter == OnlineSearchFilter.SONGS) {
                    if (searchResultsSongs.isNotEmpty()) {
                        item(key = "songs_header") {
                            Text(
                                text = "Songs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(
                            count = if (searchFilter == OnlineSearchFilter.ALL) searchResultsSongs.take(8).size else searchResultsSongs.size,
                            key = { "song_${searchResultsSongs[it].id}" }
                        ) { index ->
                            val song = searchResultsSongs[index]
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                EnhancedSongListItem(
                                    song = song.copy(artist = "${song.artist} • ${song.album}"),
                                    isPlaying = false,
                                    isCurrentSong = false,
                                    showMoreOptionsButton = true,
                                    onLongPress = { contextMenuSong = song },
                                    onMoreOptionsClick = { contextMenuSong = song },
                                    onClick = {
                                        viewModel.rememberSearch(query)
                                        playerViewModel.playOnlineSeed(song, "VYBE Radio")
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. Albums Section
                if (searchFilter == OnlineSearchFilter.ALL || searchFilter == OnlineSearchFilter.ALBUMS) {
                    if (searchResultsAlbums.isNotEmpty()) {
                        item(key = "albums_header") {
                            Text(
                                text = "Albums, Movies & Soundtracks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(searchResultsAlbums, key = { "album_${it.browseId}" }) { album ->
                            AlbumSearchCard(
                                album = album,
                                onClick = { navController.navigate("album_detail/${album.browseId}") }
                            )
                        }
                    }
                }

                // 3. Artists Section
                if (searchFilter == OnlineSearchFilter.ALL || searchFilter == OnlineSearchFilter.ARTISTS) {
                    if (searchResultsArtists.isNotEmpty()) {
                        item(key = "artists_header") {
                            Text(
                                text = "Artists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        item(key = "artists_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(searchResultsArtists, key = { it.browseId }) { artist ->
                                    ArtistSearchCard(
                                        artist = artist,
                                        onClick = {
                                            navController.navigate("artist_detail/${artist.browseId}")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (searchResultsSongs.isEmpty() && searchResultsAlbums.isEmpty() && searchResultsArtists.isEmpty()) {
                    item(key = "search_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = searchError ?: "No results found for \"$query\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                if (searchError != null) {
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick = { viewModel.submitSearch(query) }) {
                                        Text("Try again")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ── Idle / Trending View ─────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (discoveryArtists.isNotEmpty()) {
                    item(key = "discovery_artists") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Artists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(discoveryArtists, key = { "discovery_artist_${it.browseId}" }) { artist ->
                                    ArtistSearchCard(
                                        artist = artist,
                                        onClick = {
                                            navController.navigate(Screen.ArtistDetail.createRoute(artist.browseId))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // AI Recommendations row
                if ((selectedGenre == null || selectedGenre == "Trending") && aiRecommendations.isNotEmpty()) {
                    item(key = "ai_recommendations_header") {
                        Text(
                            text = "Recommended for You ✨",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(aiRecommendations, key = { it.id }) { song ->
                                RecommendationCard(
                                    song = song,
                                    onClick = {
                                        playerViewModel.playSongs(aiRecommendations, song, "AI Recommendations")
                                    },
                                    onLongClick = { contextMenuSong = song }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Trending header
                if (trendingTracks.isNotEmpty()) {
                    item(key = "trending_header") {
                        Text(
                            text = discoveryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    items(trendingTracks.size, key = { trendingTracks[it].id }) { index ->
                        val song = trendingTracks[index]
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            EnhancedSongListItem(
                                song = song.copy(artist = "${song.artist} • ${song.album}"),
                                isPlaying = false,
                                isCurrentSong = false,
                                showMoreOptionsButton = true,
                                onLongPress = { contextMenuSong = song },
                                onMoreOptionsClick = { contextMenuSong = song },
                                onClick = {
                                    playerViewModel.playSongs(trendingTracks, song, discoveryTitle)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Song Context Bottom Sheet ───────────────────────────────────────
    contextMenuSong?.let { song ->
        SongContextBottomSheet(
            song = song,
            sheetState = contextMenuSheetState,
            onDismiss = { contextMenuSong = null },
            onPlayNext = {
                playerViewModel.addSongNextToQueue(song)
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(song)
            },
            onAddToPlaylist = {
                songForPlaylist = song
            },
            isFavorite = favoriteSongIds.contains(song.id),
            onToggleFavorite = {
                playerViewModel.toggleFavoriteSpecificSong(song)
            },
            onAlbum = if (
                !song.remoteAlbumBrowseId.isNullOrBlank() ||
                song.albumId > 0L ||
                song.id.startsWith("yt_") ||
                (song.album.isNotBlank() && !song.album.equals("YouTube Music", ignoreCase = true))
            ) {
                {
                    scope.launch {
                        val albumBrowseId = viewModel.resolveAlbumBrowseId(song)
                        when {
                            !albumBrowseId.isNullOrBlank() -> navController.navigateSafely(
                                Screen.AlbumDetail.createRoute(albumBrowseId)
                            )
                            song.albumId > 0L -> navController.navigateSafely(
                                Screen.AlbumDetail.createRoute(song.albumId)
                            )
                            else -> Toast.makeText(context, "Album page is unavailable", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else null,
            onArtist = {
                scope.launch {
                    val artistBrowseId = viewModel.resolveArtistBrowseId(song)
                    when {
                        !artistBrowseId.isNullOrBlank() -> navController.navigateSafely(
                            Screen.ArtistDetail.createRoute(artistBrowseId)
                        )
                        song.artistId > 0L -> navController.navigateSafely(
                            Screen.ArtistDetail.createRoute(song.artistId)
                        )
                        else -> Toast.makeText(context, "Artist page is unavailable", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    // ── Playlist Selection Bottom Sheet ─────────────────────────────────
    songForPlaylist?.let { song ->
        PlaylistBottomSheet(
            songs = listOf(song),
            onDismiss = { songForPlaylist = null },
            bottomBarHeight = 0.dp,
            playerViewModel = playerViewModel
        )
    }
}

@Composable
private fun AlbumSearchCard(
    album: YouTubeAlbum,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubcomposeAsyncImage(
                model = album.thumbnailUrl,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp)),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                },
                error = {
                    Icon(Icons.Rounded.Album, contentDescription = null, modifier = Modifier.padding(20.dp))
                },
                success = { SubcomposeAsyncImageContent() },
            )
            Column(modifier = Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(
                    text = album.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = album.artist,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(album.type, album.year?.toString()).joinToString(" • "),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArtistSearchCard(
    artist: YouTubeArtist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = artist.thumbnailUrl,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                },
                error = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(42.dp)
                    )
                },
                success = { SubcomposeAsyncImageContent() },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecommendationCard(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column {
            AsyncImage(
                model = song.albumArtUriString,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
