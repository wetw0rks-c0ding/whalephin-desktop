package com.github.damontecres.wholphin.desktop.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import java.util.UUID

/**
 * A top-level library (user view) shown in the nav drawer.
 */
data class Library(
    val id: UUID,
    val name: String,
    val collectionType: org.jellyfin.sdk.model.api.CollectionType?,
)

/**
 * Builds the home page rows for a user. Desktop port of the app's `HomeSettingsService`;
 * resolves the user's libraries into standard rows rather than the server-configured
 * home sections (which come in a later milestone).
 */
class HomeSettingsService(
    private val api: ApiClient,
) {
    /**
     * The rows shown on the home page, plus a map of library id to display name
     */
    suspend fun getHomeRows(userId: UUID): Pair<List<HomeRowConfig>, Map<UUID, String>> {
        val libraries = getLibraries(userId)
        val names = libraries.associate { it.id to it.name }
        val rows =
            buildList {
                add(HomeRowConfig.ContinueWatching())
                add(HomeRowConfig.NextUp())
                libraries.forEach { view ->
                    add(HomeRowConfig.RecentlyAdded(view.id))
                    add(HomeRowConfig.ByParent(view.id, recursive = true))
                }
            }
        return rows to names
    }

    /**
     * The user's libraries (top-level user views), used by the nav drawer.
     */
    suspend fun getLibraries(userId: UUID): List<Library> {
        val views =
            try {
                api.userViewsApi.getUserViews(userId = userId).content.items
            } catch (ex: Exception) {
                emptyList()
            }
        return views.map { view ->
            Library(
                id = view.id,
                name = view.name ?: "Library",
                collectionType = view.collectionType,
            )
        }
    }

    suspend fun fetchRow(
        userId: UUID,
        row: HomeRowConfig,
        limit: Int = 15,
    ): List<BaseItem> =
        when (row) {
            is HomeRowConfig.ContinueWatching -> getResume(userId, limit)
            is HomeRowConfig.NextUp -> getNextUp(userId, limit)
            is HomeRowConfig.RecentlyAdded -> getLatestMedia(userId, row.parentId, limit)
            is HomeRowConfig.ByParent -> getByParent(userId, row, limit)
            else -> emptyList()
        }

    private suspend fun getResume(
        userId: UUID,
        limit: Int,
    ): List<BaseItem> {
        val request =
            GetResumeItemsRequest(
                userId = userId,
                fields = SLIM_ITEM_FIELDS,
                limit = limit,
                mediaTypes = listOf(MediaType.VIDEO),
                enableTotalRecordCount = false,
            )
        return api.itemsApi.getResumeItems(request).content.items.map { BaseItem(it) }
    }

    private suspend fun getNextUp(
        userId: UUID,
        limit: Int,
    ): List<BaseItem> {
        val request =
            GetNextUpRequest(
                userId = userId,
                fields = SLIM_ITEM_FIELDS,
                imageTypeLimit = 1,
                parentId = null,
                limit = limit,
                enableResumable = true,
                enableUserData = true,
                enableRewatching = true,
                enableTotalRecordCount = false,
            )
        return api.tvShowsApi.getNextUp(request).content.items.map { BaseItem(it) }
    }

    private suspend fun getLatestMedia(
        userId: UUID,
        parentId: UUID,
        limit: Int,
    ): List<BaseItem> {
        val request =
            GetLatestMediaRequest(
                userId = userId,
                parentId = parentId,
                fields = SLIM_ITEM_FIELDS,
                imageTypeLimit = 1,
                groupItems = true,
                limit = limit,
                isPlayed = null,
            )
        return api.userLibraryApi.getLatestMedia(request).content.map { BaseItem(it) }
    }

    private suspend fun getByParent(
        userId: UUID,
        row: HomeRowConfig.ByParent,
        limit: Int,
    ): List<BaseItem> {
        val request =
            GetItemsRequest(
                userId = userId,
                parentId = row.parentId,
                recursive = row.recursive,
                limit = limit,
                fields = SLIM_ITEM_FIELDS,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = listOf(SortOrder.ASCENDING),
                enableImageTypes = ENABLE_IMAGE_TYPES,
            )
        return api.itemsApi.getItems(request).content.items.map { BaseItem(it) }
    }

    companion object {
        val SLIM_ITEM_FIELDS: List<ItemFields> =
            listOf(
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                ItemFields.MEDIA_SOURCES,
                ItemFields.CHILD_COUNT,
                ItemFields.MEDIA_STREAMS,
            )

        val ENABLE_IMAGE_TYPES = listOf(org.jellyfin.sdk.model.api.ImageType.PRIMARY, org.jellyfin.sdk.model.api.ImageType.THUMB, org.jellyfin.sdk.model.api.ImageType.BACKDROP, org.jellyfin.sdk.model.api.ImageType.LOGO)
    }
}
