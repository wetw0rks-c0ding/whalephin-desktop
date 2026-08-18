package com.github.damontecres.wholphin.desktop.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.playback.PlaybackState
import com.github.damontecres.wholphin.desktop.playback.MpvEngine
import org.jellyfin.sdk.model.api.BaseItemKind
import org.koin.compose.koinInject
import java.util.UUID

/**
 * Full-screen playback UI with mpv backend and transport controls.
 */
@Composable
fun PlaybackScreen(
    itemId: java.util.UUID,
    type: BaseItemKind,
    initialPositionMs: Long,
    onItemClick: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    engine: MpvEngine = koinInject(),
) {
    val info by engine.info.collectAsState()
    var dragPos by remember(itemId, type) { mutableStateOf<Long?>(null) }

    LaunchedEffect(engine, itemId, type, initialPositionMs) {
        // Build Jellyfin media URL from server + item info
        val serverUrl = engine.serverUrl
        if (serverUrl.isBlank()) return@LaunchedEffect
        val mediaUrl = buildMediaUrl(serverUrl, itemId, type)
        engine.play(mediaUrl, initialPositionMs)
    }

    DisposableEffect(engine) {
        onDispose { engine.stop() }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black).playbackKeyHandler(engine)) {
        // Transport controls overlay at bottom
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Seekbar
            val maxPosition = info.durationMs.coerceAtLeast(1L)
            val displayPos = (dragPos ?: info.positionMs).coerceIn(0L, maxPosition)
            Slider(
                value = displayPos.toFloat(),
                onValueChange = { v -> dragPos = v.toLong() },
                onValueChangeFinished = {
                    dragPos?.let { engine.seek(it) }
                    dragPos = null
                },
                valueRange = 0f..info.durationMs.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.Gray,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            // Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { engine.seekRelative(-10_000) }) {
                    Icon(Icons.Filled.SkipPrevious, "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = { engine.togglePlayPause() }) {
                    val icon = if (info.state == PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow
                    Icon(icon, "Play/Pause", tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = { engine.seekRelative(10_000) }) {
                    Icon(Icons.Filled.SkipNext, "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            // Time display
            Text(
                formatTime(displayPos) + " / " + formatTime(info.durationMs),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}

// Build a Jellyfin media stream URL for mpv playback
private fun buildMediaUrl(serverUrl: String, itemId: UUID, itemKind: BaseItemKind): String {
    // Jellyfin API streaming endpoints:
    //   Videos/{id}/stream — for video items
    //   Audio/{id}/stream  — for audio items
    // Auth is handled via mpv --http-header-fields with X-Emby-Token
    val isAudio = itemKind == BaseItemKind.AUDIO
    val endpoint = if (isAudio) "Audio" else "Videos"
    return "$serverUrl/$endpoint/$itemId/stream?static=true"
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.playbackKeyHandler(engine: MpvEngine): Modifier =
    this.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.Spacebar -> { engine.togglePlayPause(); true }
            Key.DirectionLeft -> { engine.seekRelative(-5_000); true }
            Key.DirectionRight -> { engine.seekRelative(5_000); true }
            Key.DirectionUp -> { engine.setVolume((engine.info.value.volume + 5).coerceAtMost(100)); true }
            Key.DirectionDown -> { engine.setVolume((engine.info.value.volume - 5).coerceAtLeast(0)); true }
            Key.F -> { engine.togglePlayPause(); true } // fallback
            else -> false
        }
    }