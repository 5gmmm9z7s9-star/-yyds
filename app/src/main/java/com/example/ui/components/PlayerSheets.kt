package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.TrackEntity
import com.example.playback.SleepTimerMode
import com.example.playback.SleepTimerState
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.util.formatCountdownSeconds
import com.example.ui.util.formatDurationMs
import com.example.ui.util.formatSpeedLabel
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEED_PRESETS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
private val SLEEP_PRESET_MINUTES = listOf(10, 15, 30, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedControlSheet(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.speed_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.speed_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Large tabular speed readout + fine steppers
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = (sliderSpeed - 0.05f).coerceIn(0.5f, 3.0f)
                            sliderSpeed = next
                            onSelectSpeed(next)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("speed_decrease_button"),
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "减速 0.05x")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatSpeedLabel(sliderSpeed),
                            style = MonoTabularStyle.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = when {
                                abs(sliderSpeed - 1.0f) < 0.01f -> "标准原速"
                                sliderSpeed < 1.0f -> "慢速精听"
                                else -> "倍速畅听"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            val next = (sliderSpeed + 0.05f).coerceIn(0.5f, 3.0f)
                            sliderSpeed = next
                            onSelectSpeed(next)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("speed_increase_button"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "加速 0.05x")
                    }
                }
            }

            // Continuous fine slider
            Column {
                Slider(
                    value = sliderSpeed,
                    onValueChange = { raw ->
                        val quantized = ((raw * 20f).roundToInt() / 20f).coerceIn(0.5f, 3.0f)
                        sliderSpeed = quantized
                        onSelectSpeed(quantized)
                    },
                    valueRange = 0.5f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.testTag("speed_slider"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("0.5x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("1.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("2.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("3.0x", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Presets
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SPEED_PRESETS.forEach { preset ->
                    val selected = abs(sliderSpeed - preset) < 0.02f
                    FilterChip(
                        selected = selected,
                        onClick = {
                            sliderSpeed = preset
                            onSelectSpeed(preset)
                        },
                        label = {
                            Text(
                                text = formatSpeedLabel(preset),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.testTag("speed_preset_${formatSpeedLabel(preset)}"),
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    sliderSpeed = 1.0f
                    onSelectSpeed(1.0f)
                },
                enabled = abs(sliderSpeed - 1.0f) >= 0.01f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("speed_reset_button"),
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.speed_reset))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    sleepTimerState: SleepTimerState,
    onStartMinutes: (Int) -> Unit,
    onExtendMinutes: (Int) -> Unit,
    onStartEndOfTrack: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customMinutes by remember { mutableIntStateOf(25) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sleep_timer_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.sleep_timer_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Active status card
            if (sleepTimerState.isActive) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) {
                                        "当前模式"
                                    } else {
                                        "剩余时间"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    text = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) {
                                        stringResource(R.string.sleep_timer_end_of_track)
                                    } else {
                                        formatCountdownSeconds(sleepTimerState.remainingSeconds)
                                    },
                                    style = MonoTabularStyle.copy(
                                        fontSize = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) 18.sp else 28.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onCancel()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("sleep_cancel_button"),
                            ) {
                                Icon(Icons.Rounded.TimerOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sleep_timer_cancel))
                            }
                        }

                        if (sleepTimerState.mode == SleepTimerMode.COUNTDOWN && sleepTimerState.totalSeconds > 0) {
                            val progress = (sleepTimerState.remainingSeconds.toFloat() / sleepTimerState.totalSeconds.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape),
                            )
                            FilledTonalButton(
                                onClick = { onExtendMinutes(5) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("sleep_extend_button"),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sleep_timer_extend_five))
                            }
                        }
                    }
                }
            }

            // Quick duration chips
            Text(
                text = "快捷定时时长",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SLEEP_PRESET_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            onStartMinutes(minutes)
                            onDismiss()
                        },
                        label = {
                            Text(
                                text = "${minutes} 分钟",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        modifier = Modifier.testTag("sleep_preset_$minutes"),
                    )
                }

                FilterChip(
                    selected = sleepTimerState.mode == SleepTimerMode.END_OF_TRACK,
                    onClick = {
                        onStartEndOfTrack()
                        onDismiss()
                    },
                    leadingIcon = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = {
                        Text(
                            text = stringResource(R.string.sleep_timer_end_of_track),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    modifier = Modifier.testTag("sleep_end_of_track"),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Custom minutes selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_custom),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "$customMinutes 分钟",
                        style = MonoTabularStyle.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = customMinutes.toFloat(),
                    onValueChange = { customMinutes = it.roundToInt().coerceIn(5, 120) },
                    valueRange = 5f..120f,
                    modifier = Modifier.testTag("sleep_custom_slider"),
                )
                Button(
                    onClick = {
                        onStartMinutes(customMinutes)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sleep_custom_start_button"),
                ) {
                    Text("开启 $customMinutes 分钟定时关闭")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackQueueSheet(
    queue: List<TrackEntity>,
    currentIndex: Int,
    isPlaying: Boolean,
    onJumpToIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${stringResource(R.string.queue_title)} (${queue.size})",
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "当前播放队列为空",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    itemsIndexed(queue, key = { idx, item -> "${item.uriString}_$idx" }) { index, track ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToIndex(index) }
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (isCurrent) {
                                EqualizerBars(isPlaying = isPlaying)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (track.durationMs > 0L) {
                                Text(
                                    text = formatDurationMs(track.durationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            IconButton(
                                onClick = { onRemoveIndex(index) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "从队列移除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
