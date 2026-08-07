package com.github.damontecres.wholphin.data

import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

/**
 * A sort applied to a collection grid, e.g. from a library's saved settings
 */
@Serializable
data class SortAndDirection(
    val sort: ItemSortBy,
    val direction: SortOrder,
) {
    fun flip() = copy(direction = direction.flip())

    companion object {
        val DEFAULT = SortAndDirection(ItemSortBy.SORT_NAME, SortOrder.ASCENDING)
    }
}

fun SortOrder.flip() = if (this == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
