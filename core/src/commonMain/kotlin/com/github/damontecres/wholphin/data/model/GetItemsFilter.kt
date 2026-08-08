@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.VideoType
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPersonsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/**
 * Filter for a collection folder. Desktop port of the app's model; saved-filter
 * lookups are resolved by the desktop [com.github.damontecres.wholphin.desktop.data.LibraryDisplayInfoStore].
 */
@Serializable
data class CollectionFolderFilter(
    val nameOverride: String? = null,
    val filter: GetItemsFilter = GetItemsFilter(),
    /**
     * Whether to use the library's saved sort & filter
     */
    val useSavedLibraryDisplayInfo: Boolean = true,
    /**
     * Use a different ID for the library's saved sort & filter, such as for library's genres
     */
    val libraryDisplayInfoIdOverride: String? = null,
)

/**
 * A simplified filter which can be [applyTo] a [GetItemsRequest], [GetPersonsRequest] or
 * [GetArtistsRequest] to add or remove filters
 */
@Serializable
data class GetItemsFilter(
    val favorite: Boolean? = null,
    val genres: List<UUID>? = null,
    val minCommunityRating: Double? = null,
    val officialRatings: List<String>? = null,
    val persons: List<UUID>? = null,
    val played: Boolean? = null,
    val studios: List<UUID>? = null,
    val tags: List<String>? = null,
    val includeItemTypes: List<BaseItemKind>? = null,
    val videoTypes: List<FilterVideoType>? = null,
    val years: List<Int>? = null,
    val decades: List<Int>? = null,
    val override: GetItemsFilterOverride = GetItemsFilterOverride.NONE,
) {
    /**
     * Add the filtering from this into the [GetItemsRequest], overwriting the fields
     *
     * @param req the [GetItemsRequest]
     * @param overwriteIncludeTypes whether the includeItemTypes field should be overwritten (used from this) or used as-is from the [GetItemsRequest]
     */
    fun applyTo(
        req: GetItemsRequest,
        overwriteIncludeTypes: Boolean = true,
    ) = req.copy(
        includeItemTypes = if (overwriteIncludeTypes) includeItemTypes else req.includeItemTypes,
        isFavorite = favorite,
        genreIds = genres,
        minCommunityRating = minCommunityRating,
        personIds = persons,
        isPlayed = played,
        studioIds = studios,
        tags = tags,
        officialRatings = officialRatings,
        years =
            buildSet {
                years?.letNotEmpty(::addAll)
                decades?.forEach { addAll(it..<(it + 10)) }
            },
        is4k =
            videoTypes?.letNotEmpty {
                videoTypes.contains(FilterVideoType.FOUR_K).takeIf { it }
            },
        isHd =
            videoTypes?.letNotEmpty {
                if (videoTypes.contains(FilterVideoType.HD)) {
                    true
                } else if (videoTypes.contains(FilterVideoType.SD)) {
                    false
                } else {
                    null
                }
            },
        is3d =
            videoTypes?.letNotEmpty {
                videoTypes.contains(FilterVideoType.THREE_D).takeIf { it }
            },
        videoTypes =
            videoTypes?.letNotEmpty {
                it.mapNotNull { videoType ->
                    when (videoType) {
                        FilterVideoType.FOUR_K,
                        FilterVideoType.HD,
                        FilterVideoType.SD,
                        FilterVideoType.THREE_D,
                        -> null

                        FilterVideoType.BLU_RAY -> VideoType.BLU_RAY

                        FilterVideoType.DVD -> VideoType.DVD
                    }
                }
            },
    )

    fun applyTo(req: GetPersonsRequest) =
        req.copy(
            isFavorite = favorite,
        )

    fun applyTo(req: GetArtistsRequest) =
        req.copy(
            minCommunityRating = minCommunityRating,
            isFavorite = favorite,
            genreIds = genres,
            personIds = persons,
            studioIds = studios,
            tags = tags,
            officialRatings = officialRatings,
            years =
                buildSet {
                    years?.letNotEmpty(::addAll)
                    decades?.forEach { addAll(it..<(it + 10)) }
                },
        )

    /**
     * Merge another [GetItemsFilter] onto this one, replacing only unset values
     */
    fun merge(filter: GetItemsFilter): GetItemsFilter =
        this.copy(
            favorite = favorite ?: filter.favorite,
            genres = genres ?: filter.genres,
            minCommunityRating = minCommunityRating ?: filter.minCommunityRating,
            officialRatings = officialRatings ?: filter.officialRatings,
            persons = persons ?: filter.persons,
            played = played ?: filter.played,
            studios = studios ?: filter.studios,
            tags = tags ?: filter.tags,
            includeItemTypes = includeItemTypes ?: filter.includeItemTypes,
            videoTypes = videoTypes ?: filter.videoTypes,
            years = years ?: filter.years,
            decades = decades ?: filter.decades,
            override = override.takeUnless { it == GetItemsFilterOverride.NONE } ?: filter.override,
        )
}

enum class GetItemsFilterOverride {
    NONE,
    PERSON,
    ARTIST,
}

enum class FilterVideoType(val readable: String) {
    FOUR_K("4K"),
    HD("HD"),
    SD("SD"),
    THREE_D("3D"),
    BLU_RAY("Blu-Ray"),
    DVD("DVD"),
}

/**
 * Invokes [block] only when the receiver list is non-null and non-empty
 */
private inline fun <T, R> List<T>?.letNotEmpty(block: (List<T>) -> R): R? {
    if (this == null || this.isEmpty()) return null
    return block(this)
}
