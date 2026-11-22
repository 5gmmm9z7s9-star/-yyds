package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.EmptyLibraryHero
import com.example.ui.components.LibraryTopHeader
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.PlaybackQueueSheet
import com.example.ui.components.SleepTimerSheet
import com.example.ui.components.SpeedControlSheet
import com.example.ui.components.TrackRowItem
import com.example.ui.viewmodel.LibraryFilter
import com.example.ui.viewmodel.LibrarySort
import com.example.ui.viewmodel.MusicUiState
import com.example.ui.viewmodel.MusicViewModel

@Composable
fun MusicHomeScreen(
    viewModel: MusicViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showNowPlayingModal by rememberSaveable { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { },
    )

    // Pick one or multiple local audio files via SAF (zero storage permissions needed)
    val pickAudioFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importLocalFiles(uris, autoPlayFirst = uiState.playback.currentTrack == null)
            }
        },
    )

    // Pick a whole local folder via SAF
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { treeUri ->
            if (treeUri != null) {
                viewModel.importFolder(treeUri)
            }
        },
    )

    fun ensureNotificationPermissionForPlayback() {
        if (!askedNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                askedNotificationPermission = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val openFilePicker = {
        pickAudioFilesLauncher.launch(arrayOf("audio/*"))
    }
    val openFolderPicker = {
        pickFolderLauncher.launch(null)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedLayout = maxWidth >= 840.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            if (isExpandedLayout) {
                // Two-pane Studio layout for tablets / foldables
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    LibraryPane(
                        uiState = uiState,
                        showMiniPlayer = false,
                        onPickFiles = openFilePicker,
                        onPickFolder = openFolderPicker,
                        onOpenSleepTimer = { showSleepSheet = true },
                        onToggleTheme = viewModel::toggleDarkTheme,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onFilterChange = viewModel::updateFilter,
                        onSortChange = viewModel::updateSort,
                        onPlayAll = { shuffle ->
                            ensureNotificationPermissionForPlayback()
                            viewModel.playAllVisible(shuffle)
                        },
                        onTrackClick = { track ->
                            ensureNotificationPermissionForPlayback()
                            viewModel.playTrack(track)
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlayNext = viewModel::addToPlayNext,
                        onRemoveTrack = viewModel::removeTrackFromLibrary,
                        onOpenNowPlaying = { },
                        onTogglePlayPause = {
                            ensureNotificationPermissionForPlayback()
                            viewModel.togglePlayPause()
                        },
                        onSkipNext = viewModel::skipToNext,
                        onOpenSpeedSheet = { showSpeedSheet = true },
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                    )

                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    NowPlayingScreen(
                        playback = uiState.playback,
                        sleepTimer = uiState.sleepTimer,
                        isModal = false,
                        onCollapse = { },
                        onTogglePlayPause = {
                            ensureNotificationPermissionForPlayback()
                            viewModel.togglePlayPause()
                        },
                        onSeekTo = viewModel::seekTo,
                        onSkipNext = viewModel::skipToNext,
                        onSkipPrevious = viewModel::skipToPrevious,
                        onToggleRepeat = viewModel::toggleRepeatMode,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onToggleFavorite = {
                            uiState.playback.currentTrack?.let(viewModel::toggleFavorite)
                        },
                        onOpenSpeedSheet = { showSpeedSheet = true },
                        onOpenSleepSheet = { showSleepSheet = true },
                        onOpenQueueSheet = { showQueueSheet = true },
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                // Mobile Compact/Medium layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .windowInsetsPadding(WindowInsets.statusBars),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    LibraryPane(
                        uiState = uiState,
                        showMiniPlayer = uiState.playback.currentTrack != null,
                        onPickFiles = openFilePicker,
                        onPickFolder = openFolderPicker,
                        onOpenSleepTimer = { showSleepSheet = true },
                        onToggleTheme = viewModel::toggleDarkTheme,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onFilterChange = viewModel::updateFilter,
                        onSortChange = viewModel::updateSort,
                        onPlayAll = { shuffle ->
                            ensureNotificationPermissionForPlayback()
                            viewModel.playAllVisible(shuffle)
                        },
                        onTrackClick = { track ->
                            ensureNotificationPermissionForPlayback()
                            viewModel.playTrack(track)
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddToPlayNext = viewModel::addToPlayNext,
                        onRemoveTrack = viewModel::removeTrackFromLibrary,
                        onOpenNowPlaying = { showNowPlayingModal = true },
                        onTogglePlayPause = {
                            ensureNotificationPermissionForPlayback()
                            viewModel.togglePlayPause()
                        },
                        onSkipNext = viewModel::skipToNext,
                        onOpenSpeedSheet = { showSpeedSheet = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 640.dp),
                    )
                }
            }
        }

        // Full-Screen Now Playing Studio Modal (Compact / Medium)
        AnimatedVisibility(
            visible = showNowPlayingModal && !isExpandedLayout,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            NowPlayingScreen(
                playback = uiState.playback,
                sleepTimer = uiState.sleepTimer,
                isModal = true,
                onCollapse = { showNowPlayingModal = false },
                onTogglePlayPause = {
                    ensureNotificationPermissionForPlayback()
                    viewModel.togglePlayPause()
                },
                onSeekTo = viewModel::seekTo,
                onSkipNext = viewModel::skipToNext,
                onSkipPrevious = viewModel::skipToPrevious,
                onToggleRepeat = viewModel::toggleRepeatMode,
                onToggleShuffle = viewModel::toggleShuffle,
                onToggleFavorite = {
                    uiState.playback.currentTrack?.let(viewModel::toggleFavorite)
                },
                onOpenSpeedSheet = { showSpeedSheet = true },
                onOpenSleepSheet = { showSleepSheet = true },
                onOpenQueueSheet = { showQueueSheet = true },
            )
        }
    }

    if (showSpeedSheet) {
        SpeedControlSheet(
            currentSpeed = uiState.playback.playbackSpeed,
            onSelectSpeed = viewModel::setPlaybackSpeed,
            onDismiss = { showSpeedSheet = false },
        )
    }

    if (showSleepSheet) {
        SleepTimerSheet(
            sleepTimerState = uiState.sleepTimer,
            onStartMinutes = viewModel::startSleepTimerMinutes,
            onExtendMinutes = viewModel::extendSleepTimerMinutes,
            onStartEndOfTrack = viewModel::startSleepTimerEndOfTrack,
            onCancel = viewModel::cancelSleepTimer,
            onDismiss = { showSleepSheet = false },
        )
    }

    if (showQueueSheet) {
        PlaybackQueueSheet(
            queue = uiState.playback.queue,
            currentIndex = uiState.playback.currentQueueIndex,
            isPlaying = uiState.playback.isPlaying,
            onJumpToIndex = viewModel::jumpToQueueIndex,
            onRemoveIndex = viewModel::removeFromQueue,
            onDismiss = { showQueueSheet = false },
        )
    }
}

@Composable
private fun LibraryPane(
    uiState: MusicUiState,
    showMiniPlayer: Boolean,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onToggleTheme: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onTrackClick: (com.example.data.local.TrackEntity) -> Unit,
    onToggleFavorite: (com.example.data.local.TrackEntity) -> Unit,
    onAddToPlayNext: (com.example.data.local.TrackEntity) -> Unit,
    onRemoveTrack: (com.example.data.local.TrackEntity) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenSpeedSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryTopHeader(
                trackCount = uiState.allTracks.size,
                sleepTimerState = uiState.sleepTimer,
                darkTheme = uiState.darkTheme,
                onPickFiles = onPickFiles,
                onPickFolder = onPickFolder,
                onOpenSleepTimer = onOpenSleepTimer,
                onToggleTheme = onToggleTheme,
            )

            if (uiState.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.allTracks.isNotEmpty()) {
                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_clear_search),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("search_input"),
                )

                Spacer(Modifier.height(10.dp))

                // Filter Chips & Sort button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = uiState.activeFilter == LibraryFilter.ALL,
                        onClick = { onFilterChange(LibraryFilter.ALL) },
                        label = { Text(stringResource(R.string.filter_all)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.testTag("filter_all"),
                    )
                    FilterChip(
                        selected = uiState.activeFilter == LibraryFilter.FAVORITES,
                        onClick = { onFilterChange(LibraryFilter.FAVORITES) },
                        label = { Text(stringResource(R.string.filter_favorites)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.testTag("filter_favorites"),
                    )
                    FilterChip(
                        selected = uiState.activeFilter == LibraryFilter.RECENT,
                        onClick = { onFilterChange(LibraryFilter.RECENT) },
                        label = { Text(stringResource(R.string.filter_recent)) },
                        modifier = Modifier.testTag("filter_recent"),
                    )

                    Spacer(Modifier.weight(1f))

                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Rounded.Sort, contentDescription = "排序方式")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_recent_added)) },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSortChange(LibrarySort.ADDED_DESC)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_title)) },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSortChange(LibrarySort.TITLE_ASC)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_duration)) },
                                onClick = {
                                    sortMenuExpanded = false
                                    onSortChange(LibrarySort.DURATION_DESC)
                                },
                            )
                        }
                    }
                }

                // Quick Play All / Shuffle bar
                if (uiState.visibleTracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { onPlayAll(false) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("play_all_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "${stringResource(R.string.action_play_all)} (${uiState.visibleTracks.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }

                        TextButton(
                            onClick = { onPlayAll(true) },
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("shuffle_all_button"),
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_shuffle_all))
                        }
                    }
                }
            }

            // Track list or empty states
            if (uiState.visibleTracks.isEmpty()) {
                val (emptyTitle, emptySubtitle, showButtons) = when {
                    uiState.allTracks.isEmpty() -> Triple(
                        stringResource(R.string.empty_title),
                        stringResource(R.string.empty_subtitle),
                        true,
                    )
                    uiState.searchQuery.isNotBlank() -> Triple(
                        stringResource(R.string.empty_search_title),
                        stringResource(R.string.empty_search_subtitle),
                        false,
                    )
                    uiState.activeFilter == LibraryFilter.FAVORITES -> Triple(
                        stringResource(R.string.empty_favorites_title),
                        stringResource(R.string.empty_favorites_subtitle),
                        false,
                    )
                    else -> Triple(
                        "暂无播放记录",
                        "点击任意曲目开始播放后，将出现在这里",
                        false,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyLibraryHero(
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                        showImportButtons = showButtons,
                        onPickFiles = onPickFiles,
                        onPickFolder = onPickFolder,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 6.dp,
                        bottom = if (showMiniPlayer) 104.dp else 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.visibleTracks, key = { it.id }) { track ->
                        val isCurrent = uiState.playback.currentTrack?.uriString == track.uriString
                        TrackRowItem(
                            track = track,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && uiState.playback.isPlaying,
                            onClick = { onTrackClick(track) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onAddToPlayNext = { onAddToPlayNext(track) },
                            onRemove = { onRemoveTrack(track) },
                        )
                    }
                }
            }
        }

        // Persistent Floating Mini-Player
        if (showMiniPlayer && uiState.playback.currentTrack != null) {
            val currentTrack = uiState.playback.currentTrack
            val duration = uiState.playback.durationMs.coerceAtLeast(1L)
            val progress = (uiState.playback.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

            MiniPlayerBar(
                track = currentTrack,
                isPlaying = uiState.playback.isPlaying,
                playbackSpeed = uiState.playback.playbackSpeed,
                progressFraction = progress,
                onOpenNowPlaying = onOpenNowPlaying,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onOpenSpeedSheet = onOpenSpeedSheet,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
