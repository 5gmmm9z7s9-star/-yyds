package com.example.ui.util

import java.util.Locale
import kotlin.math.roundToInt

fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = (durationMs / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatCountdownSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatSpeedLabel(speed: Float): String {
    val rounded = (speed * 100f).roundToInt() / 100f
    return when {
        rounded * 10f == (rounded * 10f).roundToInt().toFloat() ->
            String.format(Locale.US, "%.1fx", rounded)
        else ->
            String.format(Locale.US, "%.2fx", rounded)
    }
}
