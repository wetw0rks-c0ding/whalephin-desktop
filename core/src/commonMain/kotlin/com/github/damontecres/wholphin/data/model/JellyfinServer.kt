@file:UseSerializers(UUIDSerializer::class, ZonedDateTimeSerializer::class)

package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.data.ZonedDateTimeSerializer
import com.github.damontecres.wholphin.preferences.SubtitleModePreference
import com.github.damontecres.wholphin.preferences.UserProfileSettings
import com.github.damontecres.wholphin.util.isNotNullOrBlank
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Represents a Jellyfin server
 */
@Serializable
data class JellyfinServer(
    val id: UUID,
    val name: String?,
    val url: String,
    val version: String?,
    val lastUsed: ZonedDateTime? = null,
) {
    val serverVersion: ServerVersion? by lazy { version?.let(ServerVersion::fromString) }
}

/**
 * Represents a Jellyfin user for a particular server
 */
@Serializable
data class JellyfinUser(
    val rowId: Int = 0,
    val id: UUID,
    val name: String?,
    val serverId: UUID,
    val accessToken: String?,
    val pin: String? = null,
    val requireLogin: Boolean = false,
    val lastUsed: ZonedDateTime? = null,
    val uiLanguage: String? = null,
    val appPreferences: JellyfinUserPreferences = JellyfinUserPreferences(),
) {
    val hasPin: Boolean get() = pin.isNotNullOrBlank()

    val isProtected: Boolean get() = hasPin || requireLogin

    override fun toString(): String =
        "JellyfinUser(rowId=$rowId, id=$id, name=$name, serverId=$serverId, lastUsed=$lastUsed, " +
            "accessToken?=${accessToken.isNotNullOrBlank()}, pin?=${pin.isNotNullOrBlank()}), " +
            "requireLogin=$requireLogin, lastUsed=$lastUsed, uiLanguage=$uiLanguage, " +
            "appPreferences=$appPreferences"
}

/**
 * Represents the relationship between [JellyfinServer] and its [JellyfinUser]
 */
data class JellyfinServerUsers(
    val server: JellyfinServer,
    val users: List<JellyfinUser>,
)

@Serializable
data class JellyfinUserPreferences(
    val preferredAudioLanguage: String = UserProfileSettings.USE_USER_PROFILE,
    val preferredSubtitleLanguage: String = UserProfileSettings.USE_USER_PROFILE,
    val subtitleMode: SubtitleModePreference = SubtitleModePreference.USE_USER_PROFILE,
)
