package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_unable_to_play_stream
import org.jetbrains.compose.resources.getString
import kotlin.time.TimeSource

private const val STARTUP_WATCHDOG_POLL_MS = 1_000L

/**
 * Turns "stream never starts" into a real failure on the desktop player.
 *
 * Restarts for every new source. When the stream stays dead it runs the same recovery chain a
 * reported player error would: refresh expiring credentials, else switch to the next autoplay
 * candidate, else show the error instead of loading forever.
 */
@Composable
internal fun PlayerScreenRuntime.PlaybackStartupWatchdogEffect(sourceUrl: String?) {
    LaunchedEffect(sourceUrl) {
        if (sourceUrl == null) return@LaunchedEffect
        val watchdog = PlaybackStartupWatchdog()
        val clock = TimeSource.Monotonic.markNow()
        while (!watchdog.hasStarted) {
            delay(STARTUP_WATCHDOG_POLL_MS)
            if (errorMessage != null) return@LaunchedEffect
            val dead = watchdog.onSnapshot(
                nowMs = clock.elapsedNow().inWholeMilliseconds,
                snapshot = playbackSnapshot,
                wantsPlayback = shouldPlay,
            )
            if (dead) {
                handleStreamNeverStarted()
                return@LaunchedEffect
            }
        }
    }
}

private suspend fun PlayerScreenRuntime.handleStreamNeverStarted() {
    val message = getString(Res.string.player_unable_to_play_stream)
    if (tryRefreshCredentialedSourceAfterError(message)) return
    if (tryAutoSwitchToNextStream()) return
    errorMessage = message
    scrubbingPositionMs = null
    controlsVisible = !playerControlsLocked
    removeFailedStreamFromCache()
}
