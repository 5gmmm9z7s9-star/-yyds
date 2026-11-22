package com.example

import com.example.playback.SleepTimerManager
import com.example.playback.SleepTimerMode
import com.example.ui.util.formatCountdownSeconds
import com.example.ui.util.formatDurationMs
import com.example.ui.util.formatSpeedLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        SleepTimerManager.getInstance().cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `time and speed formatters output expected values`() {
        assertEquals("00:00", formatDurationMs(0L))
        assertEquals("03:45", formatDurationMs(225_000L))
        assertEquals("1:02:05", formatDurationMs(3_725_000L))
        assertEquals("15:00", formatCountdownSeconds(900))
        assertEquals("1.0x", formatSpeedLabel(1.0f))
        assertEquals("1.25x", formatSpeedLabel(1.25f))
        assertEquals("2.5x", formatSpeedLabel(2.5f))
    }

    @Test
    fun `sleep timer manager switches between countdown, end of track, and cancel`() {
        val manager = SleepTimerManager.getInstance()
        manager.cancel()
        assertFalse(manager.state.value.isActive)

        manager.startCountdown(15)
        assertEquals(SleepTimerMode.COUNTDOWN, manager.state.value.mode)
        assertEquals(900, manager.state.value.remainingSeconds)
        assertTrue(manager.state.value.isActive)

        manager.extendByMinutes(5)
        assertEquals(1200, manager.state.value.remainingSeconds)

        manager.startEndOfTrack()
        assertEquals(SleepTimerMode.END_OF_TRACK, manager.state.value.mode)
        assertTrue(manager.state.value.isActive)

        manager.cancel()
        assertEquals(SleepTimerMode.OFF, manager.state.value.mode)
        assertFalse(manager.state.value.isActive)
    }
}
