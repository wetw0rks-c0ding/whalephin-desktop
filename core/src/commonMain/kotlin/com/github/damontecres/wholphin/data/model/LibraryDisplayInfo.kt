package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.data.SortAndDirection
import com.github.damontecres.wholphin.data.ViewOptions
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

/**
 * A library's saved sort/filter/view options. Desktop port of the app's Room entity,
 * stripped of Room annotations and persisted via a JSON store keyed by (serverId, itemId).
 */
@Serializable
data class LibraryDisplayInfo(
    val serverId: String,
    val itemId: String,
    val sort: ItemSortBy,
    val direction: SortOrder,
    val filter: GetItemsFilter,
    val viewOptions: ViewOptions?,
) {
    val sortAndDirection get() = SortAndDirection(sort, direction)
}
