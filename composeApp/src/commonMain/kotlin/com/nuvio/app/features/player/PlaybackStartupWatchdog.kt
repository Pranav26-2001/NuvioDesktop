package com.nuvio.app.features.player

/**
 * Detects a stream that was handed to the player but never starts.
 *
 * The desktop mpv backend is configured to keep reconnecting, and no native layer reports a failed
 * load, so a dead stream otherwise sits on the loading screen forever without raising an error.
 * This watches the playback snapshots instead: if the stream has not started playing and neither
 * the position nor the buffer has moved for [timeoutMs], it counts as dead.
 *
 * A slow but alive stream keeps its buffer growing, which resets the clock, so it is not cut off.
 */
internal class PlaybackStartupWatchdog(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    /** True once real playback was seen. From then on this watchdog never fires. */
    var hasStarted: Boolean = false
        private set

    private var initialized = false
    private var lastProgressAtMs = 0L
    private var lastPositionMs = 0L
    private var lastBufferedMs = 0L

    /**
     * Feeds one snapshot taken at [nowMs] (any monotonic millisecond clock). Returns true when the
     * stream should be treated as failed.
     *
     * [wantsPlayback] is false while the user has paused; that time must not count as a stall.
     */
    fun onSnapshot(nowMs: Long, snapshot: PlayerPlaybackSnapshot, wantsPlayback: Boolean): Boolean {
        if (hasStarted) return false
        if (snapshot.isPlaying || snapshot.isEnded) {
            hasStarted = true
            return false
        }
        if (!initialized || !wantsPlayback) {
            initialized = true
            lastProgressAtMs = nowMs
            lastPositionMs = snapshot.positionMs
            lastBufferedMs = snapshot.bufferedPositionMs
            return false
        }
        val progressed = snapshot.positionMs != lastPositionMs ||
            snapshot.bufferedPositionMs > lastBufferedMs
        lastPositionMs = snapshot.positionMs
        lastBufferedMs = snapshot.bufferedPositionMs
        if (progressed) lastProgressAtMs = nowMs
        return nowMs - lastProgressAtMs >= timeoutMs
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
