package com.github.damontecres.wholphin.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Global keyboard shortcuts for the app. Desktop equivalent of D-pad/remote input.
 *
 * Shortcuts:
 *   Esc        — go back
 *   Ctrl+H     — go to home
 *   Ctrl+S     — go to search
 *   Ctrl+F     — toggle fullscreen (browser toggle)
 *   Space      — play/pause (when playback is active)
 *   Left/Right — seek -5s / +5s (playback)
 *   Up/Down    — volume +/- 5 (playback)
 *   F          — toggle favorite (detail page)
 *   Ctrl+N     — next up (playback)
 *   Backspace  — go back
 */
object KeyShortcuts {
    const val TAG = "KeyShortcuts"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.globalKeyHandler(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSearch: () -> Unit,
    isPlaybackActive: Boolean = false,
    onPlayPause: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onVolumeUp: () -> Unit = {},
    onVolumeDown: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
): Modifier =
    this.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

        when {
            // Global navigation
            event.key == Key.Escape || event.key == Key.Backspace -> {
                onBack(); true
            }
            event.isCtrlPressed && event.key == Key.H -> {
                onHome(); true
            }
            event.isCtrlPressed && event.key == Key.S -> {
                onSearch(); true
            }

            // Playback controls (only active during playback)
            isPlaybackActive -> {
                when {
                    event.key == Key.Spacebar -> { onPlayPause(); true }
                    event.key == Key.DirectionLeft -> { onSeekBackward(); true }
                    event.key == Key.DirectionRight -> { onSeekForward(); true }
                    event.key == Key.DirectionUp -> { onVolumeUp(); true }
                    event.key == Key.DirectionDown -> { onVolumeDown(); true }
                    event.key == Key.F -> { onFavoriteToggle(); true }
                    else -> false
                }
            }

            else -> false
        }
    }