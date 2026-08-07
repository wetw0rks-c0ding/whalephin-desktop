@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.desktop.ui.nav

import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/**
 * Represents a page in the desktop app. M3 subset of the app's sealed class:
 * home, search, collection grids and item detail.
 */
@Serializable
sealed class Destination(
    val fullScreen: Boolean = false,
) {
    @Serializable
    data class Home(
        val id: Long = 0L,
    ) : Destination()

    @Serializable
    data class Search(
        val query: String = "",
    ) : Destination()

    @Serializable
    data class FilteredCollection(
        val itemId: UUID,
        val parentType: BaseItemKind,
        val filter: CollectionFolderFilter,
        val recursive: Boolean,
        val collectionType: CollectionType,
    ) : Destination()

    @Serializable
    data class MediaItem(
        val itemId: UUID,
        val type: BaseItemKind,
        val collectionType: CollectionType? = null,
    ) : Destination()

    @Serializable
    data class MoreHomeRow(
        val title: String,
        val config: HomeRowConfig,
        val initialPosition: Int = 0,
    ) : Destination()
}
