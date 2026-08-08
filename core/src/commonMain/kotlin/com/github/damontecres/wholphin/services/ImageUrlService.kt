package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageFormat
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/**
 * Builds image URLs for the configured server. The SDK returns absolute URLs carrying
 * the access token as a query parameter, so they can be loaded directly by Coil.
 */
class ImageUrlService(
    private val api: ApiClient,
) {
    fun getItemImageUrl(
        itemId: UUID,
        itemType: BaseItemKind,
        seriesId: UUID?,
        useSeriesForPrimary: Boolean,
        imageType: ImageType,
        imageTags: Map<ImageType, String?>,
        backdropTags: List<String>,
        parentThumbId: UUID? = null,
        parentBackdropId: UUID? = null,
        fillWidth: Int? = null,
        fillHeight: Int? = null,
    ): String? =
        when (imageType) {
            ImageType.LOGO -> {
                if (seriesId != null && (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)) {
                    getItemImageUrl(seriesId, imageType, fillWidth, fillHeight)
                } else {
                    getItemImageUrl(itemId, imageType, fillWidth, fillHeight)
                }
            }

            ImageType.BACKDROP -> {
                if (seriesId != null && (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)) {
                    getItemImageUrl(seriesId, imageType, fillWidth, fillHeight)
                } else if (backdropTags.isNotEmpty()) {
                    getItemImageUrl(itemId, imageType, fillWidth, fillHeight)
                } else {
                    null
                }
            }

            ImageType.THUMB -> {
                if (useSeriesForPrimary && parentThumbId != null &&
                    (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)
                ) {
                    getItemImageUrl(parentThumbId, imageType, fillWidth, fillHeight)
                } else if (useSeriesForPrimary && parentBackdropId != null &&
                    (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)
                ) {
                    getItemImageUrl(parentBackdropId, ImageType.BACKDROP, fillWidth, fillHeight)
                } else if (parentThumbId != null && itemType == BaseItemKind.SEASON && imageType !in imageTags) {
                    getItemImageUrl(parentThumbId, imageType, fillWidth, fillHeight)
                } else if (useSeriesForPrimary &&
                    parentThumbId == null &&
                    itemType == BaseItemKind.EPISODE &&
                    imageType !in imageTags
                ) {
                    // Fall back to episode image if no parent thumb
                    getItemImageUrl(itemId, ImageType.PRIMARY, fillWidth, fillHeight)
                } else if (imageType !in imageTags && backdropTags.isNotEmpty()) {
                    getItemImageUrl(itemId, ImageType.BACKDROP, fillWidth, fillHeight)
                } else {
                    getItemImageUrl(itemId, imageType, fillWidth, fillHeight)
                }
            }

            ImageType.PRIMARY,
            ImageType.BANNER,
            -> {
                if (useSeriesForPrimary && seriesId != null &&
                    (itemType == BaseItemKind.EPISODE || itemType == BaseItemKind.SEASON)
                ) {
                    getItemImageUrl(seriesId, imageType, fillWidth, fillHeight)
                } else if (seriesId != null && itemType == BaseItemKind.SEASON && imageType !in imageTags) {
                    getItemImageUrl(seriesId, imageType, fillWidth, fillHeight)
                } else {
                    getItemImageUrl(itemId, imageType, fillWidth, fillHeight)
                }
            }

            else -> getItemImageUrl(itemId, imageType, fillWidth, fillHeight)
        }

    fun getItemImageUrl(
        item: BaseItem?,
        imageType: ImageType,
        fillWidth: Int? = null,
        fillHeight: Int? = null,
        useSeriesForPrimary: Boolean? = null,
    ): String? {
        // Honour any explicit override set by the caller
        item?.imageUrlOverride?.let { return it }
        return if (item != null) {
            getItemImageUrl(
                itemId = item.id,
                itemType = item.type,
                seriesId = item.data.seriesId,
                useSeriesForPrimary = useSeriesForPrimary ?: item.useSeriesForPrimary,
                imageTags = item.data.imageTags.orEmpty(),
                imageType = imageType,
                parentThumbId = item.data.parentThumbItemId,
                parentBackdropId = item.data.parentBackdropItemId,
                backdropTags = item.data.backdropImageTags.orEmpty(),
                fillWidth = fillWidth,
                fillHeight = fillHeight,
            )
        } else {
            null
        }
    }

    fun getItemImageUrl(
        itemId: UUID,
        imageType: ImageType,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        width: Int? = null,
        height: Int? = null,
        quality: Int? = QUALITY,
        fillWidth: Int? = null,
        fillHeight: Int? = null,
        tag: String? = null,
        format: ImageFormat? = null,
        percentPlayed: Double? = null,
        unplayedCount: Int? = null,
        blur: Int? = null,
        backgroundColor: String? = null,
        foregroundLayer: String? = null,
        imageIndex: Int? = null,
    ): String? {
        if (api.baseUrl.isNullOrBlank()) return null
        return api.imageApi.getItemImageUrl(
            itemId = itemId,
            imageType = imageType,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            width = width?.takeIf { it > 0 },
            height = height?.takeIf { it > 0 },
            quality = quality,
            fillWidth = fillWidth?.takeIf { it > 0 },
            fillHeight = fillHeight?.takeIf { it > 0 },
            tag = tag,
            format = format,
            percentPlayed = percentPlayed,
            unplayedCount = unplayedCount,
            blur = blur,
            backgroundColor = backgroundColor,
            foregroundLayer = foregroundLayer,
            imageIndex = imageIndex,
        )
    }

    fun getUserImageUrl(userId: UUID) = api.imageApi.getUserImageUrl(userId)

    companion object {
        private const val QUALITY = 96
    }
}
