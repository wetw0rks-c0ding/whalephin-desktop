package com.github.damontecres.wholphin.desktop.services

import androidx.compose.runtime.mutableStateListOf
import com.github.damontecres.wholphin.data.model.CurrentUser
import com.github.damontecres.wholphin.data.model.JellyfinServer
import kotlinx.serialization.Serializable

/**
 * The top-level setup flow destinations
 */
@Serializable
sealed interface SetupDestination {
    @Serializable
    data object Loading : SetupDestination

    @Serializable
    data object ServerList : SetupDestination

    @Serializable
    data class UserList(
        val server: JellyfinServer,
    ) : SetupDestination

    @Serializable
    data class AppContent(
        val current: CurrentUser,
    ) : SetupDestination

    @Serializable
    data class PinEntry(
        val current: CurrentUser,
    ) : SetupDestination
}

/**
 * Manages navigating for setup. Desktop port of the Android `SetupNavigationManager`
 * (ACRA/Timber dropped).
 */
class SetupNavigationManager {
    val backStack: MutableList<SetupDestination> = mutableStateListOf(SetupDestination.Loading)

    /**
     * Go to the specified [SetupDestination]
     */
    fun navigateTo(destination: SetupDestination) {
        backStack[0] = destination
    }
}
