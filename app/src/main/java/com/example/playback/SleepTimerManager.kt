package com.example.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SleepTimerMode {
    OFF,
    COUNTDOWN,
    END_OF_TRACK,
}

data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
) {
    val isActive: Boolean get() = mode != SleepTimerMode.OFF
}

class SleepTimerManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var countdownJob: Job? = null
    private var attachedPlayer: Player? = null

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun attachPlayer(player: Player) {
        attachedPlayer = player
    }

    fun detachPlayer(player: Player) {
        if (attachedPlayer === player) {
            attachedPlayer = null
        }
    }

    fun startCountdown(minutes: Int) {
        val safeSeconds = (minutes.coerceIn(1, 180)) * 60
        countdownJob?.cancel()
        restorePlayerVolume()
        _state.value = SleepTimerState(
            mode = SleepTimerMode.COUNTDOWN,
            totalSeconds = safeSeconds,
            remainingSeconds = safeSeconds,
        )
        launchCountdownLoop()
    }

    fun extendByMinutes(extraMinutes: Int) {
        val current = _state.value
        if (current.mode != SleepTimerMode.COUNTDOWN) return
        val extraSeconds = extraMinutes * 60
        val newRemaining = current.remainingSeconds + extraSeconds
        val newTotal = maxOf(current.totalSeconds, newRemaining)
        restorePlayerVolume()
        _state.value = current.copy(
            totalSeconds = newTotal,
            remainingSeconds = newRemaining,
        )
    }

    fun startEndOfTrack() {
        countdownJob?.cancel()
        restorePlayerVolume()
        _state.value = SleepTimerState(
            mode = SleepTimerMode.END_OF_TRACK,
            totalSeconds = 0,
            remainingSeconds = 0,
        )
    }

    fun cancel() {
        countdownJob?.cancel()
        countdownJob = null
        restorePlayerVolume()
        _state.value = SleepTimerState()
    }

    fun onTrackAutoTransitioned() {
        if (_state.value.mode == SleepTimerMode.END_OF_TRACK) {
            attachedPlayer?.let { player ->
                player.pause()
                player.seekTo(0L)
            }
            cancel()
        }
    }

    fun onPlaybackQueueEnded() {
        if (_state.value.mode == SleepTimerMode.END_OF_TRACK) {
            attachedPlayer?.pause()
            cancel()
        }
    }

    private fun launchCountdownLoop() {
        countdownJob = scope.launch {
            while (_state.value.mode == SleepTimerMode.COUNTDOWN && _state.value.remainingSeconds > 0) {
                delay(1000L)
                val current = _state.value
                if (current.mode != SleepTimerMode.COUNTDOWN) break
                val nextRemaining = (current.remainingSeconds - 1).coerceAtLeast(0)
                _state.value = current.copy(remainingSeconds = nextRemaining)

                // Gentle 3-second audio fade-out before pausing
                attachedPlayer?.let { player ->
                    if (player.isPlaying) {
                        when (nextRemaining) {
                            3 -> player.volume = 0.72f
                            2 -> player.volume = 0.42f
                            1 -> player.volume = 0.16f
                        }
                    }
                }

                if (nextRemaining == 0) {
                    attachedPlayer?.let { player ->
                        player.pause()
                        player.volume = 1.0f
                    }
                    _state.value = SleepTimerState()
                    break
                }
            }
        }
    }

    private fun restorePlayerVolume() {
        attachedPlayer?.let { player ->
            if (player.volume < 1.0f) {
                player.volume = 1.0f
            }
        }
    }

    companion object {
        @Volatile
        private var instance: SleepTimerManager? = null

        fun getInstance(): SleepTimerManager {
            return instance ?: synchronized(this) {
                instance ?: SleepTimerManager().also { instance = it }
            }
        }
    }
}
