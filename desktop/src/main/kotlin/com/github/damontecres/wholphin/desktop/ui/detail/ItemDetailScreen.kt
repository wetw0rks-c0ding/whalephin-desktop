package com.github.damontecres.wholphin.desktop.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.desktop.ui.components.LocalImageUrlService
import com.github.damontecres.wholphin.desktop.util.DesktopViewModel
import com.github.damontecres.wholphin.desktop.util.launchIO
import com.github.damontecres.wholphin.services.ExtrasService
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.util.LoadingState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.koin.compose.koinInject

data class ItemDetailState(
    val loadingState: LoadingState = LoadingState.Pending,
    val item: BaseItem? = null,
    val trailers: List<Trailer> = emptyList(),
    val extras: List<ExtrasItem> = emptyList(),
    val actionError: String? = null,
)

class ItemDetailViewModel(
    private val api: ApiClient,
    private val itemId: UUID,
    private val trailerService: TrailerService,
    private val extrasService: ExtrasService,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(ItemDetailState())
    val state: StateFlow<ItemDetailState> = _state

    fun init() {
        viewModelScope.launchIO {
            _state.update { it.copy(loadingState = LoadingState.Loading) }
            try {
                val dto = api.userLibraryApi.getItem(itemId = itemId).content
                val item = dto?.let { BaseItem(it) }
                _state.update { it.copy(loadingState = LoadingState.Success, item = item) }
                // Load trailers and extras independently — a failure in either
                // leaves the item visible.
                if (item != null) {
                    try {
                        _state.update {
                            it.copy(
                                trailers =
                                    trailerService.getRemoteTrailers(item) + trailerService.getLocalTrailers(item),
                            )
                        }
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (_: Exception) {}
                    if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.SERIES) {
                        try {
                            _state.update { it.copy(extras = extrasService.getExtras(itemId)) }
                        } catch (ex: kotlinx.coroutines.CancellationException) {
                            throw ex
                        } catch (_: Exception) {}
                    }
                }
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _state.update { it.copy(loadingState = LoadingState.Error(exception = ex)) }
            }
        }
    }

    fun setFavorite(item: BaseItem, favorite: Boolean) {
        viewModelScope.launchIO {
            try {
                // Clear any previous action error before attempting the mutation
                _state.update { it.copy(actionError = null) }
                api.itemsApi.updateItemUserData(
                    userId = null,
                    itemId = item.id,
                    data = UpdateUserItemDataDto(isFavorite = favorite),
                )
                // Refresh after successful mutation
                val dto = api.userLibraryApi.getItem(itemId = itemId).content
                dto?.let { _state.update { s -> s.copy(item = BaseItem(it), actionError = null) } }
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _state.update { it.copy(actionError = "Failed to update favorite: ${ex.message}") }
            }
        }
    }

    fun setWatched(item: BaseItem, played: Boolean) {
        viewModelScope.launchIO {
            try {
                // Clear any previous action error before attempting the mutation
                _state.update { it.copy(actionError = null) }
                api.itemsApi.updateItemUserData(
                    userId = null,
                    itemId = item.id,
                    data = UpdateUserItemDataDto(played = played),
                )
                val dto = api.userLibraryApi.getItem(itemId = itemId).content
                dto?.let { _state.update { s -> s.copy(item = BaseItem(it), actionError = null) } }
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _state.update { it.copy(actionError = "Failed to update watched: ${ex.message}") }
            }
        }
    }

}

/**
 * Item detail page: poster, title, metadata, overview and action buttons.
 * Playback is wired in M4.
 */
@Composable
fun ItemDetailScreen(
    itemId: UUID,
    onPlay: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val api = koinInject<ApiClient>()
    val trailerService = koinInject<TrailerService>()
    val extrasService = koinInject<ExtrasService>()
    val viewModel = remember(itemId) { ItemDetailViewModel(api, itemId, trailerService, extrasService) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.init() }
    val imageUrlService = LocalImageUrlService.current

    when (val loading = state.loadingState) {
        LoadingState.Pending,
        LoadingState.Loading,
        -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        is LoadingState.Error ->
            Column(modifier = Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Unable to load item", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(loading.localizedMessage, style = MaterialTheme.typography.bodyMedium)
            }

        LoadingState.Success -> {
            val item = state.item
            if (item == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Item not found") }
            } else {
                Column(
                    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    val backdrop =
                        imageUrlService?.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = 1920, fillHeight = 1080)
                    if (backdrop != null) {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            AsyncImage(
                                model = backdrop,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Row(modifier = Modifier.padding(24.dp)) {
                        val poster =
                            imageUrlService?.getItemImageUrl(item, ImageType.PRIMARY, fillWidth = 400, fillHeight = 600)
                        if (poster != null) {
                            AsyncImage(
                                model = poster,
                                contentDescription = item.name,
                                modifier =
                                    Modifier
                                        .width(200.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        Column(
                            modifier = Modifier.padding(start = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(item.title ?: "", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            item.subtitle?.let {
                                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text =
                                    listOfNotNull(
                                        item.data.productionYear?.toString(),
                                        item.data.runTimeTicks?.let { "${it / 600_000_000L} min" },
                                    ).plus(item.data.genres.orEmpty().take(3)).joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            item.data.overview?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { onPlay(item) }, enabled = item.playable) { Text("Play") }
                                if (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.SERIES || item.type == BaseItemKind.EPISODE) {
                                    Button(onClick = { viewModel.setFavorite(item, !item.favorite) }) {
                                        Text(if (item.favorite) "Unfavorite" else "Favorite")
                                    }
                                    Button(onClick = { viewModel.setWatched(item, !item.played) }) {
                                        Text(if (item.played) "Mark unplayed" else "Mark played")
                                    }
                                }
                            }
                            state.actionError?.let { error ->
                                Spacer(Modifier.height(8.dp))
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    // Trailers row
                    val trailers = state.trailers
                    if (trailers.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Trailers",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(trailers, key = { (it as? RemoteTrailer)?.url ?: "trailer-${it.hashCode()}" }) { trailer ->
                                TrailerCard(trailer = trailer, modifier = Modifier.width(240.dp))
                            }
                        }
                    }
                    // Extras row
                    val extras = state.extras
                    if (extras.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Extras",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(extras, key = { "${it.type}_${it.title}_${it.hashCode()}" }) { extra ->
                                ExtraCard(extra = extra, modifier = Modifier.width(200.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailerCard(
    trailer: Trailer,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(trailer.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (trailer is RemoteTrailer) {
                trailer.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ExtraCard(
    extra: ExtrasItem,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(extra.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            extra.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
