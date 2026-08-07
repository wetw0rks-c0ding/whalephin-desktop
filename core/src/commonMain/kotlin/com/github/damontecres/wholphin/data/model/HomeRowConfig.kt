@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.data.SortAndDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/**
 * Configures one row on the home page. Desktop port of the app's sealed interface;
 * the `@SerialName` value is the row-type discriminator.
 */
@Serializable
sealed interface HomeRowConfig {
    val viewOptions: HomeRowViewOptions

    fun updateViewOptions(viewOptions: HomeRowViewOptions): HomeRowConfig

    @Serializable
    @SerialName("ContinueWatching")
    data class ContinueWatching(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("NextUp")
    data class NextUp(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("ContinueWatchingCombined")
    data class ContinueWatchingCombined(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("RecentlyAdded")
    data class RecentlyAdded(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("RecentlyReleased")
    data class RecentlyReleased(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("Genres")
    data class Genres(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.genreDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("Studios")
    data class Studios(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.genreDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("Favorite")
    data class Favorite(
        val kind: BaseItemKind,
        override val viewOptions: HomeRowViewOptions =
            if (kind == BaseItemKind.EPISODE) HomeRowViewOptions.episodeDefault else HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("Recordings")
    data class Recordings(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.liveTvDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("TvPrograms")
    data class TvPrograms(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.liveTvDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("TvChannels")
    data class TvChannels(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions.liveTvDefault,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("Suggestions")
    data class Suggestions(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("ByParent")
    data class ByParent(
        val parentId: UUID,
        val recursive: Boolean = false,
        val sort: SortAndDirection? = null,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }

    @Serializable
    @SerialName("GetItems")
    data class GetItems(
        val name: String,
        val getItems: GetItemsRequest,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions) = copy(viewOptions = viewOptions)
    }
}

@Serializable
@SerialName("HomePageSettings")
data class HomePageSettings(
    val rows: List<HomeRowConfig>,
    val version: Int,
) {
    companion object {
        val EMPTY = HomePageSettings(listOf(), SUPPORTED_HOME_PAGE_SETTINGS_VERSION)
    }
}

const val SUPPORTED_HOME_PAGE_SETTINGS_VERSION = 1

/**
 * View options for a home row. Defaults mirror the app's `Cards` constants.
 */
@Serializable
data class HomeRowViewOptions(
    val heightDp: Int = 172,
    val spacing: Int = 16,
    val contentScale: PrefContentScale = PrefContentScale.FILL,
    val aspectRatio: AspectRatio = AspectRatio.TALL,
    val imageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
    val showTitles: Boolean = false,
    val useSeries: Boolean = true,
    val episodeContentScale: PrefContentScale = PrefContentScale.FILL,
    val episodeAspectRatio: AspectRatio = AspectRatio.TALL,
    val episodeImageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
) {
    companion object {
        val genreDefault = HomeRowViewOptions(heightDp = 128, aspectRatio = AspectRatio.WIDE)
        val liveTvDefault = HomeRowViewOptions(heightDp = 96, aspectRatio = AspectRatio.WIDE, contentScale = PrefContentScale.FIT)
        val episodeDefault = HomeRowViewOptions()
    }
}

enum class AspectRatio(val ratio: Float) {
    TALL(2f / 3f),
    WIDE(16f / 9f),
    FOUR_THREE(4f / 3f),
    SQUARE(1f),
}

enum class ViewOptionImageType(val imageType: ImageType) {
    PRIMARY(ImageType.PRIMARY),
    THUMB(ImageType.THUMB),
}

/**
 * Plain desktop replacement for the app's generated protobuf `PrefContentScale`
 */
@Serializable
enum class PrefContentScale {
    FIT,
    NONE,
    CROP,
    FILL,
    FILL_WIDTH,
    FILL_HEIGHT,
    UNRECOGNIZED,
}
