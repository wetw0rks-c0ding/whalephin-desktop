@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.util.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.time.LocalDateTime
import java.util.UUID

/**
 * Port of Android's LatestNextUpService — specifically the periodic
 * "remediate removed-from-next-up" loop that runs in the background.
 *
 * When a user dismisses a series from "Next Up", the server records the
 * timestamp. If they later watch a new episode, this remediator re-adds
 * the series.
 */
class LatestNextUpService(
    private val api: ApiClient,
    private val displayPreferencesService: DisplayPreferencesService,
) {
    suspend fun getRemovedFromNextUp(userId: UUID): Map<UUID, LocalDateTime> =
        displayPreferencesService
            .getDisplayPreferences(userId)
            .customPrefs[REMOVED_KEY]
            ?.let { Json.decodeFromString<RemovedSeriesIds>(it).value }
            .orEmpty()

    suspend fun updateRemovedFromNextUp(userId: UUID) {
        // Collect pairs that may need removal from an initial read
        val candidates = mutableSetOf<UUID>()
        getRemovedFromNextUp(userId).forEach { (seriesId, timestamp) ->
            val item =
                api.itemsApi
                    .getItems(
                        userId = userId,
                        parentId = seriesId,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = 1,
                    ).content.items
                    .firstOrNull()
            if (item != null) {
                val lastPlayed = item.userData?.lastPlayedDate
                if (lastPlayed != null && lastPlayed > timestamp) {
                    candidates.add(seriesId)
                }
            } else {
                candidates.add(seriesId)
            }
        }
        if (candidates.isEmpty()) return
        // Inside the update lock, re-read the current value and only
        // remove entries that are still present.
        displayPreferencesService.updateDisplayPreferences(userId) {
            val current =
                get(REMOVED_KEY)
                    ?.let { Json.decodeFromString<RemovedSeriesIds>(it).value }
                    .orEmpty()
                    .toMutableMap()
            if (current.isEmpty()) return@updateDisplayPreferences
            current.keys.removeAll(candidates)
            put(REMOVED_KEY, Json.encodeToString(RemovedSeriesIds(current)))
        }
    }

    companion object {
        const val REMOVED_KEY = "removeNextUp"
    }
}

@Serializable
data class RemovedSeriesIds(
    val value: Map<UUID, LocalDateTime>,
)