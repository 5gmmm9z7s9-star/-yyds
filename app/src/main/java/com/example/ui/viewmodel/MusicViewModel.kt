package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.importer.LocalAudioImporter
import com.example.data.local.AppDatabase
import com.example.data.local.TrackEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MusicRepository
import com.example.playback.PlaybackService
import com.example.playback.SleepTimerManager
import com.example.playback.SleepTimerState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

enum class LibraryFilter {
    ALL,
    FAVORITES,
    RECENT,
}

enum class LibrarySort {
    ADDED_DESC,
    TITLE_ASC,
    DURATION_DESC,
}

data class PlaybackSnapshot(
    val currentTrack: TrackEntity? = null,
    val queue: List<TrackEntity> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: Int = Player.REPEAT_MODE_ALL,
    val shuffleEnabled: Boolean = false,
)

data class MusicUiState(
    val allTracks: List<TrackEntity> = emptyList(),
    val visibleTracks: List<TrackEntity> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: LibraryFilter = LibraryFilter.ALL,
    val activeSort: LibrarySort = LibrarySort.ADDED_DESC,
    val isImporting: Boolean = false,
    val darkTheme: Boolean = true,
    val playback: PlaybackSnapshot = PlaybackSnapshot(),
    val sleepTimer: SleepTimerState = SleepTimerState(),
)

private data class LibraryControls(
    val searchQuery: String,
    val filter: LibraryFilter,
    val sort: LibrarySort,
    val isImporting: Boolean,
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val importer = LocalAudioImporter(application)
    private val repository = MusicRepository(database.trackDao(), importer)
    private val prefsRepository = UserPreferencesRepository(application)
    private val sleepTimerManager = SleepTimerManager.getInstance()

    private val searchQuery = MutableStateFlow("")
    private val activeFilter = MutableStateFlow(LibraryFilter.ALL)
    private val activeSort = MutableStateFlow(LibrarySort.ADDED_DESC)
    private val isImporting = MutableStateFlow(false)
    private val playbackSnapshot = MutableStateFlow(PlaybackSnapshot())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionPollingJob: Job? = null
    private var latestTrackMap: Map<String, TrackEntity> = emptyMap()

    private val libraryControlsFlow = combine(
        searchQuery,
        activeFilter,
        activeSort,
        isImporting,
    ) { query, filter, sort, importing ->
        LibraryControls(query, filter, sort, importing)
    }

    val uiState: StateFlow<MusicUiState> = combine(
        repository.allTracks,
        libraryControlsFlow,
        prefsRepository.preferences,
        playbackSnapshot,
        sleepTimerManager.state,
    ) { tracks, controls, prefs, playback, sleepTimer ->
        latestTrackMap = tracks.associateBy { it.uriString }
        val filtered = filterAndSortTracks(tracks, controls)
        val enrichedCurrent = playback.currentTrack?.let { current ->
            latestTrackMap[current.uriString] ?: current
        }
        val enrichedQueue = playback.queue.map { q -> latestTrackMap[q.uriString] ?: q }

        MusicUiState(
            allTracks = tracks,
            visibleTracks = filtered,
            searchQuery = controls.searchQuery,
            activeFilter = controls.filter,
            activeSort = controls.sort,
            isImporting = controls.isImporting,
            darkTheme = prefs.darkTheme,
            playback = playback.copy(
                currentTrack = enrichedCurrent,
                queue = enrichedQueue,
                playbackSpeed = prefs.playbackSpeed,
            ),
            sleepTimer = sleepTimer,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MusicUiState(),
    )

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncSnapshotFromPlayer()
            if (isPlaying) {
                startPositionPolling()
            } else {
                stopPositionPolling()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncSnapshotFromPlayer()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncSnapshotFromPlayer()
            mediaItem?.mediaId?.let { uriStr ->
                viewModelScope.launch {
                    prefsRepository.setLastPlayedUri(uriStr)
                    latestTrackMap[uriStr]?.let { repository.markPlayed(it.id) }
                }
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            syncSnapshotFromPlayer()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncSnapshotFromPlayer()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncSnapshotFromPlayer()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncSnapshotFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            _events.tryEmit("无法播放该音频文件，可能已被移动或无访问权限")
            syncSnapshotFromPlayer()
        }
    }

    init {
        connectController()
    }

    private fun connectController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { controller ->
                    mediaController = controller
                    controller.addListener(playerListener)
                    viewModelScope.launch {
                        val prefs = prefsRepository.preferences.first()
                        controller.setPlaybackSpeed(prefs.playbackSpeed)
                        controller.repeatMode = prefs.repeatMode
                        controller.shuffleModeEnabled = prefs.shuffleEnabled
                        if (controller.mediaItemCount == 0) {
                            restoreLastSessionQueue(prefs.lastPlayedUri)
                        }
                        syncSnapshotFromPlayer()
                        if (controller.isPlaying) {
                            startPositionPolling()
                        }
                    }
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private suspend fun restoreLastSessionQueue(lastPlayedUri: String?) {
        val controller = mediaController ?: return
        val tracks = repository.allTracks.first()
        if (tracks.isEmpty()) return
        val startIdx = if (lastPlayedUri != null) {
            tracks.indexOfFirst { it.uriString == lastPlayedUri }.coerceAtLeast(0)
        } else {
            0
        }
        val mediaItems = withContext(Dispatchers.IO) {
            tracks.map { buildMediaItem(it) }
        }
        if (controller.mediaItemCount == 0) {
            controller.setMediaItems(mediaItems, startIdx, 0L)
            controller.prepare()
            controller.pause()
        }
    }

    fun importLocalFiles(uris: List<Uri>, autoPlayFirst: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isImporting.value = true
            try {
                val result = repository.importAudioUris(uris)
                when {
                    result.importedTracks.isNotEmpty() -> {
                        val msg = buildString {
                            append("已导入 ${result.importedTracks.size} 首本地音频")
                            if (result.skippedDuplicates > 0) {
                                append("（跳过 ${result.skippedDuplicates} 首重复）")
                            }
                        }
                        _events.emit(msg)
                        if (autoPlayFirst) {
                            val firstTrack = result.importedTracks.first()
                            val all = repository.allTracks.first()
                            playTrack(firstTrack, all.ifEmpty { result.importedTracks })
                        }
                    }
                    result.skippedDuplicates > 0 -> {
                        _events.emit("所选文件已在曲库中")
                        if (autoPlayFirst) {
                            val firstUri = uris.first().toString()
                            val all = repository.allTracks.first()
                            all.find { it.uriString == firstUri }?.let { playTrack(it, all) }
                        }
                    }
                    else -> {
                        _events.emit("未能读取所选音频文件，请确认文件格式有效")
                    }
                }
            } finally {
                isImporting.value = false
            }
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch {
            isImporting.value = true
            try {
                val result = repository.importFolderUri(treeUri)
                when {
                    result.importedTracks.isNotEmpty() -> {
                        _events.emit("从文件夹成功导入 ${result.importedTracks.size} 首音频")
                    }
                    result.skippedDuplicates > 0 -> {
                        _events.emit("文件夹内的音频已全部在曲库中")
                    }
                    else -> {
                        _events.emit("该文件夹下未找到可播放的音频文件")
                    }
                }
            } finally {
                isImporting.value = false
            }
        }
    }

    fun playTrack(track: TrackEntity, queueSource: List<TrackEntity> = uiState.value.visibleTracks) {
        val controller = mediaController ?: return
        val listToPlay = queueSource.ifEmpty { listOf(track) }
        val startIndex = listToPlay.indexOfFirst { it.uriString == track.uriString }.let {
            if (it >= 0) it else 0
        }
        viewModelScope.launch {
            val mediaItems = withContext(Dispatchers.IO) {
                listToPlay.map { buildMediaItem(it) }
            }
            val speed = prefsRepository.preferences.first().playbackSpeed
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.setPlaybackSpeed(speed)
            controller.prepare()
            controller.play()
            repository.markPlayed(track.id)
            prefsRepository.setLastPlayedUri(track.uriString)
            syncSnapshotFromPlayer()
        }
    }

    fun playAllVisible(shuffle: Boolean = false) {
        val visible = uiState.value.visibleTracks
        if (visible.isEmpty()) {
            _events.tryEmit("当前列表暂无可播放的曲目")
            return
        }
        val controller = mediaController ?: return
        viewModelScope.launch {
            controller.shuffleModeEnabled = shuffle
            prefsRepository.setShuffleEnabled(shuffle)
            val startTrack = if (shuffle) visible.random() else visible.first()
            playTrack(startTrack, visible)
        }
    }

    fun addToPlayNext(track: TrackEntity) {
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) {
            playTrack(track, listOf(track))
            return
        }
        viewModelScope.launch {
            val mediaItem = withContext(Dispatchers.IO) { buildMediaItem(track) }
            val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            controller.addMediaItem(insertIndex, mediaItem)
            _events.emit("已添加到下一首播放：${track.title}")
            syncSnapshotFromPlayer()
        }
    }

    fun jumpToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekToDefaultPosition(index)
            controller.play()
            syncSnapshotFromPlayer()
        }
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
            syncSnapshotFromPlayer()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.mediaItemCount == 0) {
                val visible = uiState.value.visibleTracks
                if (visible.isNotEmpty()) {
                    playTrack(visible.first(), visible)
                }
            } else {
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs.coerceAtLeast(0L))
        playbackSnapshot.value = playbackSnapshot.value.copy(positionMs = positionMs.coerceAtLeast(0L))
    }

    fun seekRelative(deltaMs: Long) {
        val controller = mediaController ?: return
        val duration = controller.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (controller.currentPosition + deltaMs).coerceIn(0L, duration)
        seekTo(target)
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        }
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
        controller.repeatMode = nextMode
        viewModelScope.launch {
            prefsRepository.setRepeatMode(nextMode)
        }
        syncSnapshotFromPlayer()
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val next = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = next
        viewModelScope.launch {
            prefsRepository.setShuffleEnabled(next)
        }
        syncSnapshotFromPlayer()
    }

    fun setPlaybackSpeed(speed: Float) {
        val quantized = ((speed.coerceIn(0.5f, 3.0f) * 20f).roundToInt() / 20f)
        mediaController?.setPlaybackSpeed(quantized)
        viewModelScope.launch {
            prefsRepository.setPlaybackSpeed(quantized)
        }
        playbackSnapshot.value = playbackSnapshot.value.copy(playbackSpeed = quantized)
    }

    fun startSleepTimerMinutes(minutes: Int) {
        sleepTimerManager.startCountdown(minutes)
        _events.tryEmit("已开启定时关闭：$minutes 分钟后暂停播放")
    }

    fun extendSleepTimerMinutes(minutes: Int = 5) {
        sleepTimerManager.extendByMinutes(minutes)
        _events.tryEmit("定时关闭已延长 $minutes 分钟")
    }

    fun startSleepTimerEndOfTrack() {
        sleepTimerManager.startEndOfTrack()
        _events.tryEmit("将在播完当前歌曲后自动暂停")
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
        _events.tryEmit("已关闭定时任务")
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            repository.setFavorite(track.id, !track.isFavorite)
        }
    }

    fun removeTrackFromLibrary(track: TrackEntity) {
        viewModelScope.launch {
            repository.removeTrack(track.id)
            _events.emit("已从曲库移除「${track.title}」")
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateFilter(filter: LibraryFilter) {
        activeFilter.value = filter
    }

    fun updateSort(sort: LibrarySort) {
        activeSort.value = sort
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            prefsRepository.setDarkTheme(!uiState.value.darkTheme)
        }
    }

    private fun startPositionPolling() {
        if (positionPollingJob?.isActive == true) return
        positionPollingJob = viewModelScope.launch {
            while (true) {
                val controller = mediaController
                if (controller != null) {
                    val dur = controller.duration.takeIf { it > 0L }
                        ?: playbackSnapshot.value.currentTrack?.durationMs
                        ?: 0L
                    playbackSnapshot.value = playbackSnapshot.value.copy(
                        positionMs = controller.currentPosition.coerceAtLeast(0L),
                        durationMs = dur,
                        isPlaying = controller.isPlaying,
                    )
                }
                delay(250L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    private fun syncSnapshotFromPlayer() {
        val controller = mediaController ?: return
        val count = controller.mediaItemCount
        val queue = buildList {
            for (i in 0 until count) {
                val item = controller.getMediaItemAt(i)
                val uriStr = item.mediaId
                add(
                    latestTrackMap[uriStr] ?: TrackEntity(
                        uriString = uriStr,
                        title = item.mediaMetadata.title?.toString() ?: "本地音频",
                        artist = item.mediaMetadata.artist?.toString() ?: "未知艺术家",
                        album = item.mediaMetadata.albumTitle?.toString() ?: "本地音频",
                        durationMs = 0L,
                        formatLabel = "AUDIO",
                    )
                )
            }
        }
        val currentIdx = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem
        val currentTrack = currentItem?.let { item ->
            latestTrackMap[item.mediaId] ?: queue.getOrNull(currentIdx)
        }
        val dur = controller.duration.takeIf { it > 0L } ?: currentTrack?.durationMs ?: 0L

        playbackSnapshot.value = PlaybackSnapshot(
            currentTrack = currentTrack,
            queue = queue,
            currentQueueIndex = currentIdx,
            isPlaying = controller.isPlaying,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = dur,
            playbackSpeed = controller.playbackParameters.speed,
            repeatMode = controller.repeatMode,
            shuffleEnabled = controller.shuffleModeEnabled,
        )
    }

    private fun buildMediaItem(track: TrackEntity): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)

        track.coverPath?.let { path ->
            runCatching {
                val file = File(path)
                if (file.exists() && file.length() in 1..(512 * 1024)) {
                    metadataBuilder.setArtworkData(
                        file.readBytes(),
                        MediaMetadata.PICTURE_TYPE_FRONT_COVER,
                    )
                }
            }
        }

        return MediaItem.Builder()
            .setMediaId(track.uriString)
            .setUri(Uri.parse(track.uriString))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun filterAndSortTracks(
        tracks: List<TrackEntity>,
        controls: LibraryControls,
    ): List<TrackEntity> {
        val query = controls.searchQuery.trim()
        val filtered = tracks.asSequence()
            .filter { track ->
                when (controls.filter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.FAVORITES -> track.isFavorite
                    LibraryFilter.RECENT -> track.lastPlayedAt != null
                }
            }
            .filter { track ->
                if (query.isEmpty()) true
                else {
                    track.title.contains(query, ignoreCase = true) ||
                        track.artist.contains(query, ignoreCase = true) ||
                        track.album.contains(query, ignoreCase = true)
                }
            }
            .toList()

        return when (controls.filter) {
            LibraryFilter.RECENT -> filtered.sortedByDescending { it.lastPlayedAt ?: 0L }
            else -> when (controls.sort) {
                LibrarySort.ADDED_DESC -> filtered.sortedByDescending { it.addedAt }
                LibrarySort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
                LibrarySort.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
            }
        }
    }

    override fun onCleared() {
        stopPositionPolling()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
