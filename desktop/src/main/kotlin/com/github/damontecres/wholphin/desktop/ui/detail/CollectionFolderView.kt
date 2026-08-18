package com.github.damontecres.wholphin.desktop.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.SortAndDirection
import com.github.damontecres.wholphin.data.ViewOptions
import com.github.damontecres.wholphin.desktop.data.LibraryDisplayInfoStore
import com.github.damontecres.wholphin.util.LoadingState
import java.util.UUID
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.compose.koinInject

/**
 * A library / collection grid with a sort menu. Desktop port of the app's
 * `CollectionFolderView` + `CollectionFolderGeneric` for the M3 subset.
 */
@Composable
fun CollectionFolderScreen(
    itemId: UUID,
    collectionType: CollectionType?,
    recursive: Boolean,
    filter: CollectionFolderFilter,
    onItemClick: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = rememberCollectionFolderViewModel(itemId, collectionType, recursive, filter)
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.init() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.item?.name ?: "Library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            SortMenu(
                currentSort = state.sortAndDirection,
                onSortChange = { viewModel.onSortChange(it) },
            )
        }
        when (val loading = state.loadingState) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            is LoadingState.Error ->
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(loading.localizedMessage, style = MaterialTheme.typography.bodyMedium)
                }

            LoadingState.Success ->
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items.size) { index ->
                        val item = state.items[index]
                        if (item != null) {
                            CollectionItemRow(
                                item = item,
                                onClick = { onItemClick(item) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun CollectionItemRow(
    item: BaseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = koinInject<ImageUrlService>()
    val imageUrl = remember(item.id) {
        imageUrlService.getItemImageUrl(item, ImageType.PRIMARY, fillWidth = 80, fillHeight = 120)
    }
    Row(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(60.dp).aspectRatio(2f / 3f)) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.name?.take(2) ?: "?", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = item.name ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge,
            )
            item.data.productionYear?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SortMenu(
    currentSort: SortAndDirection,
    onSortChange: (SortAndDirection) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Text(
                text = "Sort",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CollectionFolderViewModel.VIDEO_SORT_OPTIONS.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.readableName()) },
                    onClick = {
                        expanded = false
                        val order = if (sort == currentSort.sort) currentSort.direction else currentSort.direction
                        onSortChange(SortAndDirection(sort, order))
                    },
                )
            }
        }
    }
}

private fun ItemSortBy.readableName(): String =
    when (this) {
        ItemSortBy.SORT_NAME -> "Name"
        ItemSortBy.DATE_CREATED -> "Date added"
        ItemSortBy.PREMIERE_DATE -> "Premiere date"
        ItemSortBy.PRODUCTION_YEAR -> "Year"
        ItemSortBy.PLAY_COUNT -> "Play count"
        ItemSortBy.COMMUNITY_RATING -> "Community rating"
        ItemSortBy.DATE_PLAYED -> "Date played"
        ItemSortBy.CRITIC_RATING -> "Critic rating"
        else -> toString()
    }

@Composable
fun rememberCollectionFolderViewModel(
    itemId: UUID,
    collectionType: CollectionType?,
    recursive: Boolean,
    filter: CollectionFolderFilter,
): CollectionFolderViewModel {
    val api = koinInject<ApiClient>()
    val serverRepository = koinInject<ServerRepository>()
    val libraryDisplayInfoStore = koinInject<LibraryDisplayInfoStore>()
    val vm =
        remember(itemId, collectionType, recursive, filter) {
            CollectionFolderViewModel(
                api = api,
                serverRepository = serverRepository,
                libraryDisplayInfoStore = libraryDisplayInfoStore,
                itemId = itemId,
                collectionFilter = filter,
                recursive = recursive,
                collectionType = collectionType,
            )
        }
    DisposableEffect(vm) {
        onDispose { vm.clear() }
    }
    return vm
}
