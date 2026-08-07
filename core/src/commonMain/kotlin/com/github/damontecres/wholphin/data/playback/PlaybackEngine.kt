package com.github.damontecres.wholphin.data.playback

import kotlinx.coroutines.flow.StateFlow

/**
 * Playback states shared by all backends (mpv on desktop, Media3 on Android).
 */
enum class PlaybackState {
    /** No media loaded */
    Idle,

    /** Loaded but not yet playing (buffering / starting) */
    Buffering,

    /** Playing (audio or video) */
    Playing,

    /** Paused by the user or the app */
    Paused,

    /** Media ended / reached the end */
    Ended,

    /** An error occurred and playback stopped */
    Error,
}

/**
 * Snapshot of the current playback position/speed that the UI observes.
 */
data class PlaybackInfo(
    val state: PlaybackState = PlaybackState.Idle,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = 100,
) {
    val playing: Boolean
        get() = state == PlaybackState.Playing || state == PlaybackState.Buffering

    val positionFraction: Float
        get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}

/**
 * Abstracts a playback backend so the UI (and later MPRIS, screensaver backdrops,
 * theme songs) can drive any engine uniformly. Desktop implementation is
 * [com.github.damontecres.wholphin.desktop.playback.MpvEngine]; the Android app keeps
 * its Media3-based player.
 */
interface PlaybackEngine {
    /** Live playback state, updated by the engine. */
    val info: StateFlow<PlaybackInfo>

    /**
     * Start playing [url] from [startPositionMs].
     */
    suspend fun play(
        url: String,
        startPositionMs: Long = 0,
    )

    fun pause()
    fun resume()
    fun togglePlayPause()

    /** Seek to an absolute position (ms). */
    fun seek(positionMs: Long)

    /** Seek by a relative offset (ms, can be negative). */
    fun seekRelative(offsetMs: Long)

    fun setVolume(percent: Int)

    fun stop()

    /** Stop playback and free all resources. */
    fun release()
}
