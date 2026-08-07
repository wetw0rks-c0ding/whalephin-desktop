package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.displayName
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.sortOrder
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ExtraType
import org.jellyfin.sdk.model.api.ImageType

/**
 * Get extras for media. Desktop port of the Android app's ExtrasService
 * without Android Context or resources dependencies.
 */
class ExtrasService(
    private val api: ApiClient,
    private val imageUrlService: ImageUrlService,
) {
    suspend fun getExtras(itemId: java.util.UUID): List<ExtrasItem> {
        val extrasMap =
            api.userLibraryApi
                .getSpecialFeatures(itemId)
                .content
                .filterNot {
                    it.extraType == ExtraType.THEME_SONG ||
                        it.extraType == ExtraType.THEME_VIDEO ||
                        it.extraType == ExtraType.TRAILER
                }.map { BaseItem(it) }
                .groupBy { it.data.extraType ?: ExtraType.UNKNOWN }

        return extrasMap
            .mapNotNull { (type, items) ->
                if (items.size == 1) {
                    val item = items.first()
                    val title = item.title?.takeIf { it.isNotBlank() } ?: type.displayName()
                    val subtitle = if (item.title?.isNotBlank() == true) type.displayName() else null
                    ExtrasItem.Single(
                        type = type,
                        item = item,
                        title = title,
                        subtitle = subtitle,
                        imageUrl = imageUrlService.getItemImageUrl(item, ImageType.PRIMARY),
                    )
                } else if (items.size > 1) {
                    val title = type.displayName()
                    val subtitle = "$title (${items.size})"
                    ExtrasItem.Group(
                        type = type,
                        items = items,
                        title = title,
                        subtitle = subtitle,
                        isPlayed = items.all { it.played },
                        imageUrl = imageUrlService.getItemImageUrl(items.first(), ImageType.PRIMARY),
                    )
                } else {
                    null
                }
            }.sortedBy { it.type.sortOrder }
    }
}
