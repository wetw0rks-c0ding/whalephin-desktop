package com.github.damontecres.wholphin.desktop.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.util.LoadingState
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Placeholder playback screen for M4. Real implementation with mpv render
 * surface + overlay controls comes next.
 */
@Composable
fun PlaybackScreen(
    itemId: java.util.UUID,
    type: BaseItemKind,
    initialPositionMs: Long,
    onItemClick: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf<LoadingState>(LoadingState.Loading) }
    LaunchedEffect(itemId) {
        // TODO: wire up MpvEngine + playback overlay in next step
        state = LoadingState.Error(message = "Playback screen not yet implemented")
    }

    when (val loading = state) {
        LoadingState.Loading ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        is LoadingState.Error ->
            Column(modifier = Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Playback not yet implemented", style = MaterialTheme.typography.titleLarge)
                Text(loading.localizedMessage, style = MaterialTheme.typography.bodyMedium)
            }
        else -> Box(modifier = Modifier.fillMaxSize())
    }
}