package com.github.damontecres.wholphin.desktop.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.desktop.services.HomeRowService
import com.github.damontecres.wholphin.desktop.ui.detail.CollectionFolderScreen
import com.github.damontecres.wholphin.desktop.ui.detail.ItemDetailScreen
import com.github.damontecres.wholphin.desktop.ui.main.HomePage
import com.github.damontecres.wholphin.desktop.ui.main.SearchPage
import com.github.damontecres.wholphin.desktop.ui.settings.SettingsPage
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import com.github.damontecres.wholphin.data.model.BaseItem
import org.koin.compose.koinInject

/**
 * Renders the current [Destination] in the content area.
 */
@Composable
fun DestinationContent(
    destination: Destination,
    onBack: () -> Unit,
    onItemClick: (BaseItem) -> Unit,
    onPlay: (BaseItem) -> Unit,
    onViewMore: (HomeRowConfig, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (destination !is Destination.Home && destination !is Destination.Search && destination !is Destination.Playback) {
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    titleFor(destination),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (destination) {
                is Destination.Home -> HomePage(onItemClick = onItemClick, onViewMore = onViewMore)
                is Destination.Search -> SearchPage(initialQuery = destination.query, onItemClick = onItemClick)
                is Destination.FilteredCollection ->
                    CollectionFolderScreen(
                        itemId = destination.itemId,
                        collectionType = destination.collectionType,
                        recursive = destination.recursive,
                        filter = destination.filter,
                        onItemClick = onItemClick,
                    )

                is Destination.MediaItem ->
                    ItemDetailScreen(
                        itemId = destination.itemId,
                        onPlay = onPlay,
                    )

                is Destination.MoreHomeRow ->
                    MoreHomeRowContent(
                        title = destination.title,
                        config = destination.config,
                        onItemClick = onItemClick,
                    )

                is Destination.Playback ->
                    PlaybackScreen(
                        itemId = destination.itemId,
                        type = destination.type,
                        initialPositionMs = destination.positionMs,
                        onItemClick = onItemClick,
                    )

                is Destination.Settings ->
                    SettingsPage(onBack = onBack)

                is Destination.Favorites ->
                    FavoritesPage(onItemClick = onItemClick)
            }
        }
    }
}

private fun titleFor(destination: Destination): String =
    when (destination) {
        is Destination.FilteredCollection -> "Library"
        is Destination.MediaItem -> "Details"
        is Destination.MoreHomeRow -> destination.title
        is Destination.Playback -> "Now Playing"
        is Destination.Settings -> "Settings"
        is Destination.Favorites -> "Favorites"
        else -> ""
    }

/**
 * Renders a full home row (all items, not just the preview). Uses [HomeRowService]
 * to fetch the complete list.
 */
@Composable
private fun MoreHomeRowContent(
    title: String,
    config: HomeRowConfig,
    onItemClick: (BaseItem) -> Unit,
) {
    val homeRowService = koinInject<HomeRowService>()
    val state by homeRowService.state.collectAsState()

    androidx.compose.runtime.LaunchedEffect(config) { homeRowService.load(config) }

    when (val loading = state.loadingState) {
        com.github.damontecres.wholphin.util.LoadingState.Pending,
        com.github.damontecres.wholphin.util.LoadingState.Loading,
        -> Box(Modifier.fillMaxSize()) { androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center)) }

        is com.github.damontecres.wholphin.util.LoadingState.Error ->
            Text(loading.localizedMessage, modifier = Modifier.padding(48.dp))

        com.github.damontecres.wholphin.util.LoadingState.Success ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items.size) { index ->
                    Text(
                        text = "Item $index",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
    }
}

/**
 * Favorites page showing items the user has marked as favorite across all libraries.
 */
@Composable
private fun FavoritesPage(
    onItemClick: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollectionFolderScreen(
        itemId = UUID(0L, 0L),
        collectionType = CollectionType.FOLDERS,
        recursive = true,
        filter =
            CollectionFolderFilter(
                filter = GetItemsFilter(favorite = true),
                useSavedLibraryDisplayInfo = false,
            ),
        onItemClick = onItemClick,
        modifier = modifier,
    )
}
