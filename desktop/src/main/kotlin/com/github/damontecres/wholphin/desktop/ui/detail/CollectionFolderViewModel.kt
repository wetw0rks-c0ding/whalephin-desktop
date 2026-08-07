package com.github.damontecres.wholphin.desktop.ui.detail

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.SortAndDirection
import com.github.damontecres.wholphin.data.ViewOptions
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.LibraryDisplayInfo
import com.github.damontecres.wholphin.desktop.data.LibraryDisplayInfoStore
import com.github.damontecres.wholphin.desktop.util.DesktopViewModel
import com.github.damontecres.wholphin.desktop.util.launchIO
import com.github.damontecres.wholphin.util.Log
import com.github.damontecres.wholphin.util.LoadingState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

data class CollectionFolderState(
    val loadingState: LoadingState = LoadingState.Pending,
    val item: BaseItem? = null,
    val items: List<BaseItem?> = emptyList(),
    val sortAndDirection: SortAndDirection = SortAndDirection.DEFAULT,
    val viewOptions: ViewOptions = ViewOptions(),
) {
    companion object {
        val EMPTY = CollectionFolderState()
    }
}

/**
 * Loads one library / collection grid. Desktop port of the app's `CollectionFolderViewModel`.
 */
class CollectionFolderViewModel(
    private val api: ApiClient,
    private val serverRepository: ServerRepository,
    private val libraryDisplayInfoStore: LibraryDisplayInfoStore,
    private val itemId: UUID,
    private val collectionFilter: CollectionFolderFilter,
    private val recursive: Boolean,
    private val collectionType: CollectionType?,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(CollectionFolderState.EMPTY)
    val state: StateFlow<CollectionFolderState> = _state

    private var loadGeneration = 0

    fun init() {
        val filter = collectionFilter.filter
        val savedSort = loadSavedSort()
        _state.update { it.copy(sortAndDirection = savedSort) }
        val generation = ++loadGeneration
        viewModelScope.launchIO {
            loadItem()
            loadItems(filter, generation)
        }
    }

    fun onSortChange(sortAndDirection: SortAndDirection) {
        val generation = ++loadGeneration
        _state.update { it.copy(sortAndDirection = sortAndDirection) }
        saveSort(sortAndDirection)
        viewModelScope.launchIO { loadItems(collectionFilter.filter, generation) }
    }

    private suspend fun loadItem() {
        try {
            val item = api.userLibraryApi.getItem(itemId = itemId).content
            _state.update { it.copy(item = item?.let { BaseItem(it) }) }
        } catch (ex: Exception) {
            Log.e(ex, "Error loading collection item $itemId")
        }
    }

    private suspend fun loadItems(
        filter: com.github.damontecres.wholphin.data.model.GetItemsFilter,
        generation: Int,
    ) {
        _state.update { state ->
            if (generation != loadGeneration) return@update state
            state.copy(loadingState = LoadingState.Loading)
        }
        try {
            val sort = _state.value.sortAndDirection
            val request =
                filter.applyTo(
                    GetItemsRequest(
                        userId = serverRepository.current.value?.user?.id,
                        parentId = itemId,
                        recursive = recursive,
                        includeItemTypes = collectionType.baseItemKinds,
                        sortBy = listOfNotNull(sort.sort, ItemSortBy.SORT_NAME),
                        sortOrder = listOf(sort.direction),
                        fields = SLIM_FIELDS,
                        enableImageTypes = ENABLE_IMAGE_TYPES,
                        enableTotalRecordCount = false,
                    ),
                )
            val result = api.itemsApi.getItems(request).content.items
            _state.update { state ->
                if (generation != loadGeneration) return@update state
                state.copy(loadingState = LoadingState.Success, items = result.map { BaseItem(it) })
            }
        } catch (ex: Exception) {
            Log.e(ex, "Error loading collection items for $itemId")
            _state.update { state ->
                if (generation != loadGeneration) return@update state
                state.copy(loadingState = LoadingState.Error(exception = ex))
            }
        }
    }

    private fun loadSavedSort(): SortAndDirection {
        val serverId = serverRepository.current.value?.server?.id?.toString() ?: return SortAndDirection.DEFAULT
        val saved = libraryDisplayInfoStore.get(serverId, itemId.toString())
        return saved?.sortAndDirection ?: SortAndDirection.DEFAULT
    }

    private fun saveSort(sortAndDirection: SortAndDirection) {
        val serverId = serverRepository.current.value?.server?.id?.toString() ?: return
        libraryDisplayInfoStore.set(
            LibraryDisplayInfo(
                serverId = serverId,
                itemId = itemId.toString(),
                sort = sortAndDirection.sort,
                direction = sortAndDirection.direction,
                filter = collectionFilter.filter,
                viewOptions = null,
            ),
        )
    }

    companion object {
        val SLIM_FIELDS: List<ItemFields> =
            listOf(
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.MEDIA_SOURCES,
                ItemFields.CHILD_COUNT,
                ItemFields.MEDIA_STREAMS,
            )

        val ENABLE_IMAGE_TYPES =
            listOf(
                org.jellyfin.sdk.model.api.ImageType.PRIMARY,
                org.jellyfin.sdk.model.api.ImageType.THUMB,
                org.jellyfin.sdk.model.api.ImageType.BACKDROP,
                org.jellyfin.sdk.model.api.ImageType.LOGO,
            )

        val VIDEO_SORT_OPTIONS: List<ItemSortBy> =
            listOf(
                ItemSortBy.SORT_NAME,
                ItemSortBy.DATE_CREATED,
                ItemSortBy.PREMIERE_DATE,
                ItemSortBy.PRODUCTION_YEAR,
                ItemSortBy.PLAY_COUNT,
                ItemSortBy.COMMUNITY_RATING,
                ItemSortBy.DATE_PLAYED,
                ItemSortBy.CRITIC_RATING,
            )
    }
}

/**
 * Which item types a collection type contains, mirroring the app's `CollectionType.baseItemKinds`.
 */
val CollectionType?.baseItemKinds: List<BaseItemKind>?
    get() =
        when (this) {
            CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
            CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES, BaseItemKind.SEASON, BaseItemKind.EPISODE)
            CollectionType.MUSIC -> listOf(BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST, BaseItemKind.AUDIO)
            CollectionType.MUSICVIDEOS -> listOf(BaseItemKind.MUSIC_VIDEO)
            CollectionType.BOXSETS -> listOf(BaseItemKind.BOX_SET)
            CollectionType.PLAYLISTS -> listOf(BaseItemKind.PLAYLIST)
            CollectionType.HOMEVIDEOS, CollectionType.PHOTOS -> listOf(BaseItemKind.VIDEO, BaseItemKind.PHOTO, BaseItemKind.PHOTO_ALBUM)
            CollectionType.LIVETV -> listOf(BaseItemKind.LIVE_TV_CHANNEL, BaseItemKind.LIVE_TV_PROGRAM)
            CollectionType.FOLDERS, CollectionType.TRAILERS, CollectionType.BOOKS, CollectionType.UNKNOWN -> null
            else -> null
        }
