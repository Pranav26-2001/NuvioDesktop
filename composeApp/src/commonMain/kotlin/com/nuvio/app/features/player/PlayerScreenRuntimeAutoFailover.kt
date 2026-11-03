package com.nuvio.app.features.player

import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.streams.StreamAutoPlaySelector
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_stream_failed_trying_next
import org.jetbrains.compose.resources.getString

/** Upper bound on automatic switches per playback session, so a bad provider can't loop forever. */
private const val MAX_AUTOPLAY_FAILOVERS = 8

/**
 * Called when the stream autoplay chose can't be played. Moves to the next candidate autoplay
 * ranked, without the user having to touch anything.
 *
 * Returns true when a switch was started, in which case the caller must not show an error.
 * Returns false when there is nothing to fall back to (manually picked stream, queue used up,
 * or the failover cap was hit) and the normal error handling should run.
 */
internal fun PlayerScreenRuntime.tryAutoSwitchToNextStream(): Boolean {
    if (autoPlayFallbackQueue.isEmpty()) return false
    if (autoPlayFailoverCount >= MAX_AUTOPLAY_FAILOVERS) return false

    activeSourceUrl.takeIf { it.isNotBlank() }?.let { failedAutoPlaySourceUrls += it }
    // Don't let "reuse last link" replay the dead link next time this title is opened.
    removeFailedStreamFromCache()

    val next = autoPlayFallbackQueue.firstOrNull { candidate ->
        val url = candidate.playableDirectUrl
        (url == null || url !in failedAutoPlaySourceUrls) &&
            StreamAutoPlaySelector.isAutoPlayable(candidate)
    }
    if (next == null) {
        autoPlayFallbackQueue = emptyList()
        return false
    }

    // Drop everything up to and including the stream we are about to try.
    autoPlayFallbackQueue = autoPlayFallbackQueue.dropWhile { it !== next }.drop(1)
    autoPlayFailoverCount += 1
    errorMessage = null

    scope.launch {
        NuvioToastController.show(getString(Res.string.player_stream_failed_trying_next))
    }
    switchToSource(next, keepAutoPlayQueue = true)
    return true
}
