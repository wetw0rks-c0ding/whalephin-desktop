@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import kotlin.time.Duration

/**
 * The central media-item model. Desktop equivalent of the Android app's `BaseItem`,
 * without the compose/UI-only members (card corner text, AnnotatedString, etc.).
 */
@Serializable
data class BaseItem(
    val data: BaseItemDto,
    val useSeriesForPrimary: Boolean = false,
    val imageUrlOverride: String? = null,
) {
    val id get() = data.id

    val gridId get() = id.toString()

    val playable: Boolean
        get() = type.playable

    val sortName: String
        get() = data.sortName ?: data.name ?: ""

    val type get() = data.type

    val name get() = data.name

    val title get() = if (type == BaseItemKind.EPISODE) data.seriesName else name

    val subtitle: String?
        get() =
            when (type) {
                BaseItemKind.EPISODE -> listOf(data.seasonEpisode, name).joinNotBlank(" - ")
                BaseItemKind.SERIES -> data.productionYear?.toString()
                BaseItemKind.AUDIO -> listOf(data.album, artistsString).joinNotBlank(" - ")
                else -> data.productionYear?.toString()
            }

    val canDelete: Boolean get() = data.canDelete == true

    val aspectRatio: Float? get() = data.primaryImageAspectRatio?.toFloat()?.takeIf { it > 0 }

    val indexNumber get() = data.indexNumber

    val playbackPosition get() = data.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val resumeMs get() = playbackPosition.inWholeMilliseconds

    val played get() = data.userData?.played ?: false

    val favorite get() = data.userData?.isFavorite ?: false

    private val artistsString: String?
        get() = data.artists.orEmpty().joinToString(", ").takeIf { it.isNotBlank() }
}

private val BaseItemDto.seasonEpisode: String?
    get() =
        if (parentIndexNumber != null && indexNumber != null) {
            "S${parentIndexNumber}E${indexNumber}"
        } else {
            null
        }

private fun List<String?>.joinNotBlank(sep: String): String? =
    filter { !it.isNullOrBlank() }.joinToString(sep).takeIf { it.isNotBlank() }

private val BaseItemKind.playable: Boolean
    get() =
        this in
            setOf(
                BaseItemKind.MOVIE,
                BaseItemKind.SERIES,
                BaseItemKind.SEASON,
                BaseItemKind.EPISODE,
                BaseItemKind.AUDIO,
                BaseItemKind.MUSIC_ALBUM,
                BaseItemKind.MUSIC_ARTIST,
                BaseItemKind.VIDEO,
                BaseItemKind.BOX_SET,
                BaseItemKind.PLAYLIST,
                BaseItemKind.MUSIC_VIDEO,
                BaseItemKind.PROGRAM,
            )
