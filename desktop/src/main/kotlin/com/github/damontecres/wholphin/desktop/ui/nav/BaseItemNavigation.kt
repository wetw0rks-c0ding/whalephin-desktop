package com.github.damontecres.wholphin.desktop.ui.nav

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

/**
 * Maps a [BaseItem] to a [Destination]. Libraries (collection/user views) open the
 * library grid; everything else opens the item detail page.
 */
fun BaseItem.toDestination(collectionType: CollectionType? = null): Destination =
    when (type) {
        BaseItemKind.COLLECTION_FOLDER,
        BaseItemKind.USER_VIEW,
        BaseItemKind.FOLDER,
        -> Destination.FilteredCollection(
            itemId = id,
            parentType = type ?: BaseItemKind.FOLDER,
            filter = CollectionFolderFilter(),
            recursive = true,
            collectionType = collectionType ?: CollectionType.FOLDERS,
        )

        else ->
            Destination.MediaItem(
                itemId = id,
                type = type ?: BaseItemKind.FOLDER,
                collectionType = collectionType,
            )
    }
