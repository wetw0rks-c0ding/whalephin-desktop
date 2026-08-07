@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/**
 * Resume position for a media item, persisted between sessions. Desktop equivalent of
 * the app's `ItemPlayback` Room entity.
 */
@Serializable
data class ItemPlayback(
    val itemId: UUID,
    val positionMs: Long,
    val lastPlayed: Long,
)
