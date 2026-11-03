package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStartupWatchdogTest {

    private fun loading(position: Long = 0L, buffered: Long = 0L) = PlayerPlaybackSnapshot(
        isLoading = true,
        positionMs = position,
        bufferedPositionMs = buffered,
    )

    private val playing = PlayerPlaybackSnapshot(
        isLoading = false,
        isPlaying = true,
        positionMs = 1_000L,
    )

    @Test
    fun `fails when loading never makes progress`() {
        val watchdog = PlaybackStartupWatchdog(timeoutMs = 1_000L)

        assertFalse(watchdog.onSnapshot(0L, loading(), wantsPlayback = true))
        assertFalse(watchdog.onSnapshot(500L, loading(), wantsPlayback = true))
        assertTrue(watchdog.onSnapshot(1_000L, loading(), wantsPlayback = true))
    }

    @Test
    fun `growing buffer keeps a slow stream alive`() {
        val watchdog = PlaybackStartupWatchdog(timeoutMs = 1_000L)

        assertFalse(watchdog.onSnapshot(0L, loading(), wantsPlayback = true))
        assertFalse(watchdog.onSnapshot(900L, loading(buffered = 500L), wantsPlayback = true))
        assertFalse(watchdog.onSnapshot(1_800L, loading(buffered = 900L), wantsPlayback = true))
        assertTrue(watchdog.onSnapshot(2_800L, loading(buffered = 900L), wantsPlayback = true))
    }

    @Test
    fun `playback starting stops the watchdog for good`() {
        val watchdog = PlaybackStartupWatchdog(timeoutMs = 1_000L)

        assertFalse(watchdog.onSnapshot(0L, loading(), wantsPlayback = true))
        assertFalse(watchdog.onSnapshot(100L, playing, wantsPlayback = true))
        assertTrue(watchdog.hasStarted)
        assertFalse(watchdog.onSnapshot(50_000L, loading(), wantsPlayback = true))
    }

    @Test
    fun `time spent paused does not count as a stall`() {
        val watchdog = PlaybackStartupWatchdog(timeoutMs = 1_000L)

        assertFalse(watchdog.onSnapshot(0L, loading(), wantsPlayback = false))
        assertFalse(watchdog.onSnapshot(5_000L, loading(), wantsPlayback = false))
        assertFalse(watchdog.onSnapshot(5_500L, loading(), wantsPlayback = true))
        assertTrue(watchdog.onSnapshot(6_000L, loading(), wantsPlayback = true))
    }
}
