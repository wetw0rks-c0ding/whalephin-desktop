package com.github.damontecres.wholphin.data

import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.model.api.ExtraType
import org.jellyfin.sdk.model.api.ImageType

/**
 * Represents "extras" for media such as behind-the-scenes or deleted scenes.
 * Desktop port of the Android app's ExtrasItem, without Android resource dependencies.
 */
sealed interface ExtrasItem {
    val type: ExtraType
    val imageUrl: String?
    val title: String
    val subtitle: String?
    val isPlayed: Boolean

    /**
     * Represents multiple extras of the same type
     */
    data class Group(
        val items: List<BaseItem>,
        override val imageUrl: String?,
        override val title: String,
        override val subtitle: String,
        override val isPlayed: Boolean,
        override val type: ExtraType,
    ) : ExtrasItem

    /**
     * Represents a single extra
     */
    data class Single(
        val item: BaseItem,
        override val imageUrl: String?,
        override val title: String,
        override val subtitle: String?,
        override val type: ExtraType,
    ) : ExtrasItem {
        override val isPlayed: Boolean
            get() = item.played
    }
}

/** Human-readable label for each [ExtraType], used in place of Android string resources. */
fun ExtraType.displayName(): String =
    when (this) {
        ExtraType.UNKNOWN -> "Other Extras"
        ExtraType.CLIP -> "Clips"
        ExtraType.TRAILER -> "Trailers"
        ExtraType.BEHIND_THE_SCENES -> "Behind the Scenes"
        ExtraType.DELETED_SCENE -> "Deleted Scenes"
        ExtraType.INTERVIEW -> "Interviews"
        ExtraType.SCENE -> "Scenes"
        ExtraType.SAMPLE -> "Samples"
        ExtraType.THEME_SONG -> "Theme Songs"
        ExtraType.THEME_VIDEO -> "Theme Videos"
        ExtraType.FEATURETTE -> "Featurettes"
        ExtraType.SHORT -> "Shorts"
    }

internal val ExtraType.sortOrder: Int
    get() =
        when (this) {
            ExtraType.TRAILER -> 0
            ExtraType.FEATURETTE -> 1
            ExtraType.SHORT -> 2
            ExtraType.CLIP -> 3
            ExtraType.SCENE -> 4
            ExtraType.SAMPLE -> 5
            ExtraType.DELETED_SCENE -> 6
            ExtraType.INTERVIEW -> 7
            ExtraType.BEHIND_THE_SCENES -> 8
            ExtraType.THEME_SONG -> 9
            ExtraType.THEME_VIDEO -> 10
            ExtraType.UNKNOWN -> 11
        }