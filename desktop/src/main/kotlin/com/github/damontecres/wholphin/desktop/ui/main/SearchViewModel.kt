package com.github.damontecres.wholphin.desktop.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.desktop.util.DesktopViewModel
import com.github.damontecres.wholphin.desktop.util.launchIO
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

enum class SearchType(val label: String, val itemKinds: List<BaseItemKind>) {
    MOVIES("Movies", listOf(BaseItemKind.MOVIE)),
    SERIES("Series", listOf(BaseItemKind.SERIES)),
    EPISODES("Episodes", listOf(BaseItemKind.EPISODE)),
    COLLECTIONS("Collections", listOf(BaseItemKind.BOX_SET, BaseItemKind.PLAYLIST)),
    ALBUMS("Albums", listOf(BaseItemKind.MUSIC_ALBUM)),
    ARTISTS("Artists", listOf(BaseItemKind.MUSIC_ARTIST)),
    SONGS("Songs", listOf(BaseItemKind.AUDIO)),
}

data class SearchState(
    val query: String = "",
    val results: Map<SearchType, DataLoadingState<List<BaseItem?>>> = emptyMap(),
) {
    companion object {
        val EMPTY = SearchState()
    }
}

/**
 * Searches the server per item type, mirroring the app's `SearchViewModel` for the
 * common types (voice search and Seerr results come later).
 */
class SearchViewModel(
    private val api: ApiClient,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(SearchState.EMPTY)
    val state: StateFlow<SearchState> = _state

    fun search(query: String) {
        if (query.isBlank()) {
            _state.update { SearchState.EMPTY }
            return
        }
        _state.update { it.copy(query = query) }
        val results = _state.value.results
        SearchType.entries.forEach { type ->
            if (results[type] == null) {
                _state.update { state -> state.copy(results = state.results + (type to DataLoadingState.Loading)) }
            }
            viewModelScope.launchIO {
                val result =
                    runCatching { fetch(type, query) }
                _state.update { state ->
                    state.copy(results = state.results + (type to DataLoadingState.of(result)))
                }
            }
        }
    }

    private suspend fun fetch(
        type: SearchType,
        query: String,
    ): List<BaseItem?> {
        val request =
            GetItemsRequest(
                searchTerm = query,
                recursive = true,
                includeItemTypes = type.itemKinds,
                fields = SEARCH_FIELDS,
                limit = 50,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                enableImageTypes = listOf(org.jellyfin.sdk.model.api.ImageType.PRIMARY, org.jellyfin.sdk.model.api.ImageType.THUMB, org.jellyfin.sdk.model.api.ImageType.BACKDROP),
            )
        return api.itemsApi.getItems(request).content.items.map { BaseItem(it) }
    }

    companion object {
        val SEARCH_FIELDS: List<ItemFields> =
            listOf(
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.MEDIA_SOURCES,
                ItemFields.MEDIA_STREAMS,
                ItemFields.OVERVIEW,
            )
    }
}
