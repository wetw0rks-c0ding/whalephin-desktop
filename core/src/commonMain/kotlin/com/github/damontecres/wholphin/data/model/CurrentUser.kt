package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.Serializable

/**
 * The currently selected [JellyfinServer] and [JellyfinUser]
 */
@Serializable
data class CurrentUser(
    val server: JellyfinServer,
    val user: JellyfinUser,
)
