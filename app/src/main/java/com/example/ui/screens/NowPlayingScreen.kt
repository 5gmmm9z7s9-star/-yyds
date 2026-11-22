package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.media3.common.Player
import com.example.R
import com.example.playback.SleepTimerMode
import com.example.playback.SleepTimerState
import com.example.ui.components.RotatingVinylDisc
import com.example.ui.util.formatCountdownSeconds
import com.example.ui.util.formatDurationMs
import com.example.ui.util.formatSpeedLabel
import com.example.ui.viewmodel.PlaybackSnapshot
import kotlin.math.abs

@Composable
fun NowPlayingScreen(
    playback: PlaybackSnapshot,
    sleepTimer: SleepTimerState,
    isModal: Boolean,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenSpeedSheet: () -> Unit,
    onOpenSleepSheet: () -> Unit,
    onOpenQueueSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isModal) {
        BackHandler(onBack = onCollapse)
    }

    val track = playback.currentTrack
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = playback.durationMs.coerceAtLeast(1L)
    val liveFraction = (playback.positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val sliderFraction = if (isDraggingSlider) dragFraction else liveFraction
    val displayPositionMs = if (isDraggingSlider) {
        (dragFraction * safeDuration).toLong()
    } else {
        playback.positionMs
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface,
        )
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
            .then(if (isModal) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier),
    ) {
        val discSize = min(maxWidth * 0.66f, maxHeight * 0.36f).coerceIn(176.dp, 300.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isModal) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("collapse_player_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_collapse_player),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "正在播放",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (track != null) {
                        Text(
                            text = "${track.formatLabel} · 本地离线",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    enabled = track != null,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_favorite_button"),
                ) {
                    Icon(
                        imageVector = if (track?.isFavorite == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (track?.isFavorite == true) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Rotating vinyl artwork
            RotatingVinylDisc(
                coverPath = track?.coverPath,
                isPlaying = playback.isPlaying,
                size = discSize,
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Track Metadata
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = track?.title ?: "未选择曲目",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (track != null) "${track.artist} · ${track.album}" else "请先从曲库选择本地音频文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SeekBar + Timecodes
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderFraction,
                    onValueChange = { fraction ->
                        isDraggingSlider = true
                        dragFraction = fraction
                    },
                    onValueChangeFinished = {
                        onSeekTo((dragFraction * safeDuration).toLong())
                        isDraggingSlider = false
                    },
                    enabled = track != null && playback.durationMs > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_seek_slider"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDurationMs(displayPositionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDurationMs(playback.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_shuffle_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "随机播放",
                        tint = if (playback.shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    enabled = track != null,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("player_previous_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous),
                        modifier = Modifier.size(34.dp),
                    )
                }

                FilledIconButton(
                    onClick = onTogglePlayPause,
                    enabled = track != null,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("player_play_pause_button"),
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (playback.isPlaying) R.string.cd_pause else R.string.cd_play),
                        modifier = Modifier.size(38.dp),
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    enabled = track != null,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("player_next_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        modifier = Modifier.size(34.dp),
                    )
                }

                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_repeat_button"),
                ) {
                    val repeatIcon = if (playback.repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    }
                    val repeatActive = playback.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "循环模式",
                        tint = if (repeatActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Audiophile Quick Studio Actions: Speed | Sleep Timer | Queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val speedHighlighted = abs(playback.playbackSpeed - 1.0f) >= 0.01f
                StudioActionPill(
                    icon = Icons.Rounded.Speed,
                    label = formatSpeedLabel(playback.playbackSpeed),
                    subLabel = "倍速播放",
                    isHighlighted = speedHighlighted,
                    highlightColor = MaterialTheme.colorScheme.primary,
                    highlightContainer = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onOpenSpeedSheet,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_speed_pill"),
                )

                val sleepLabel = when (sleepTimer.mode) {
                    SleepTimerMode.OFF -> "未开启"
                    SleepTimerMode.END_OF_TRACK -> "播完本首"
                    SleepTimerMode.COUNTDOWN -> formatCountdownSeconds(sleepTimer.remainingSeconds)
                }
                StudioActionPill(
                    icon = Icons.Rounded.Bedtime,
                    label = sleepLabel,
                    subLabel = "定时关闭",
                    isHighlighted = sleepTimer.isActive,
                    highlightColor = MaterialTheme.colorScheme.tertiary,
                    highlightContainer = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = onOpenSleepSheet,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_sleep_pill"),
                )

                StudioActionPill(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "${playback.queue.size} 首",
                    subLabel = "播放队列",
                    isHighlighted = false,
                    highlightColor = MaterialTheme.colorScheme.primary,
                    highlightContainer = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onOpenQueueSheet,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_queue_pill"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StudioActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subLabel: String,
    isHighlighted: Boolean,
    highlightColor: Color,
    highlightContainer: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isHighlighted) highlightContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlighted) highlightColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.height(64.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = subLabel,
                    tint = if (isHighlighted) highlightColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isHighlighted) highlightColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
