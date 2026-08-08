package com.github.damontecres.wholphin.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.damontecres.wholphin.data.model.CurrentUser
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.PreferenceStorage
import com.github.damontecres.wholphin.util.Log
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.toServerString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Handles managing the current server & user as well as adding & removing new ones.
 *
 * Desktop port of the Android `ServerRepository` (Hilt/Context/SharedPreferences/AppPreferences
 * proto removed; current-server/current-user ids persist through [PreferenceStorage]).
 */
class ServerRepository(
    val apiClient: ApiClient,
    val serverDao: ServerDao,
    private val preferenceStorage: PreferenceStorage,
    private val ioDispatcher: CoroutineDispatcher = WholphinDispatchers.IO,
) {
    companion object {
        /** Preference key for the id of the last-used server, shared with platform startup code */
        val CURRENT_SERVER_ID_KEY = stringPreferencesKey("currentServerId")

        /** Preference key for the id of the last-used user, shared with platform startup code */
        val CURRENT_USER_ID_KEY = stringPreferencesKey("currentUserId")
    }

    private var _current = MutableStateFlow<CurrentUser?>(null)
    val current: StateFlow<CurrentUser?> = _current

    private var _currentUserDto = MutableStateFlow<UserDto?>(null)
    val currentUserDto: UserDto? get() = _currentUserDto.value
    val currentUserDtoFlow: StateFlow<UserDto?> get() = _currentUserDto

    val currentServer: JellyfinServer? get() = _current.value?.server
    val currentServerFlow: Flow<JellyfinServer?> get() = _current.map { it?.server }
    val currentUser: JellyfinUser? get() = _current.value?.user

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserFlow: Flow<JellyfinUser?>
        get() =
            _current.flatMapLatest { user ->
                if (user?.user != null) {
                    serverDao.getUserFlow(user.user.serverId, user.user.id)
                } else {
                    flow { emit(null) }
                }
            }

    /**
     * Adds a server to the app database and updates the [ApiClient] to the server's URL
     *
     * The current user is removed
     */
    suspend fun addAndChangeServer(server: JellyfinServer) {
        withContext(ioDispatcher) {
            serverDao.addOrUpdateServer(server)
        }
        apiClient.update(baseUrl = server.url, accessToken = null)
        _current.value = null
    }

    /**
     * Saves the server & User to the app database and updates the [ApiClient] to use this server & user
     */
    suspend fun changeUser(
        server: JellyfinServer,
        user: JellyfinUser,
    ): CurrentUser =
        withContext(ioDispatcher) {
            if (server.id != user.serverId) {
                throw IllegalStateException("User is not part of the server")
            }
            Log.v("Changing user to ${user.name} on ${server.url}")
            // Save previous client state in case validation fails
            val previousBaseUrl = apiClient.baseUrl
            val previousToken = apiClient.accessToken
            apiClient.update(baseUrl = server.url, accessToken = user.accessToken)
            try {
                val userDto by apiClient.userApi.getCurrentUser()
                val updatedServer =
                    try {
                        val sysInfo by apiClient.systemApi.getPublicSystemInfo()
                        server.copy(name = sysInfo.serverName, version = sysInfo.version)
                    } catch (ex: Exception) {
                        Log.w(ex, "Exception fetching public system info")
                        server
                    }
                var updatedUser =
                    user.copy(
                        id = userDto.id,
                        name = userDto.name,
                    )
                serverDao.addOrUpdateServer(updatedServer)
                updatedUser = serverDao.addOrUpdateUser(updatedUser)
                preferenceStorage.put(CURRENT_SERVER_ID_KEY, updatedServer.id.toServerString())
                preferenceStorage.put(CURRENT_USER_ID_KEY, updatedUser.id.toServerString())
                val currentUser = CurrentUser(updatedServer, updatedUser)
                withContext(WholphinDispatchers.Main) {
                    _current.value = currentUser
                    _currentUserDto.value = userDto
                }
                return@withContext currentUser
            } catch (ex: Exception) {
                // Restore previous credentials so existing sessions aren't lost
                apiClient.update(baseUrl = previousBaseUrl, accessToken = previousToken)
                throw ex
            }
        }

    /**
     * Restores a session for the given server & user such as when the app reopens
     *
     * If user has a PIN, this returns false
     */
    suspend fun restoreSession(
        serverId: UUID?,
        userId: UUID?,
    ): CurrentUser? {
        if (serverId == null || userId == null) {
            _current.value = null
            return null
        }
        val serverAndUsers =
            withContext(ioDispatcher) {
                serverDao.getServer(serverId)
            }
        if (serverAndUsers != null) {
            val current = _current.value
            if (current != null && current.server.id == serverId && current.user.id == userId) {
                Log.v("Restoring session for current user, so shortcut")
                apiClient.update(
                    baseUrl = current.server.url,
                    accessToken = current.user.accessToken,
                )
                return current
            } else {
                val user = serverAndUsers.users.firstOrNull { it.id == userId }
                if (user != null) {
                    // Don't bypass PIN — restoring a PIN-protected user requires
                    // explicit PIN entry, so return the current user for the UI
                    // to handle authentication before calling changeUser.
                    if (user.pin != null) {
                        val pending = CurrentUser(serverAndUsers.server, user)
                        _current.value = pending
                        return pending
                    }
                    return changeUser(serverAndUsers.server, user)
                }
            }
        }
        return null
    }

    suspend fun fetchLastUsedServer(serverId: UUID?): JellyfinServer? =
        withContext(ioDispatcher) {
            serverId?.let { serverDao.getServer(serverId)?.server }
        }

    fun closeSession() {
        _current.value = null
    }

    /**
     * Given a successful [AuthenticationResult], switch to the user that just authenticated
     */
    suspend fun changeUser(
        serverUrl: String,
        authenticationResult: AuthenticationResult,
        existingUser: JellyfinUser?,
    ) = withContext(ioDispatcher) {
        val accessToken = authenticationResult.accessToken
        if (accessToken != null) {
            val authedUser = authenticationResult.user
            val server =
                authenticationResult.serverId?.toUUIDOrNull()?.let {
                    JellyfinServer(
                        id = it,
                        name = authedUser?.serverName,
                        url = serverUrl,
                        null,
                    )
                }
            if (server != null) {
                val user =
                    authedUser?.let {
                        if (existingUser != null) {
                            Log.d("Re-using existing user")
                            existingUser.copy(
                                // If the server authenticated via the server, always remove the PIN
                                pin = null,
                                accessToken = accessToken,
                                lastUsed = ZonedDateTime.now(),
                            )
                        } else {
                            Log.d("Creating new user")
                            JellyfinUser(
                                id = it.id,
                                name = it.name,
                                serverId = server.id,
                                accessToken = accessToken,
                                lastUsed = ZonedDateTime.now(),
                            )
                        }
                    }
                if (user != null) {
                    return@withContext changeUser(server, user)
                } else {
                    throw IllegalArgumentException("Authentication result's user was null")
                }
            } else {
                throw IllegalArgumentException("Authentication result's serverId not valid: ${authenticationResult.serverId}")
            }
        } else {
            throw IllegalArgumentException("Authentication result's access token was null")
        }
    }

    suspend fun removeUser(user: JellyfinUser) {
        if (current.value?.user?.id == user.id && current.value?.server?.id == user.serverId) {
            withContext(WholphinDispatchers.Main) {
                _current.value = null
            }
            preferenceStorage.remove(CURRENT_USER_ID_KEY)
            apiClient.update(accessToken = null)
        }
        withContext(ioDispatcher) {
            serverDao.deleteUser(user.serverId, user.id)
        }
    }

    suspend fun removeServer(server: JellyfinServer) {
        if (current.value?.server?.id == server.id) {
            withContext(WholphinDispatchers.Main) {
                _current.value = null
            }
            preferenceStorage.remove(CURRENT_SERVER_ID_KEY)
            preferenceStorage.remove(CURRENT_USER_ID_KEY)
            apiClient.update(baseUrl = null, accessToken = null)
        }
        withContext(ioDispatcher) {
            serverDao.deleteServer(server.id)
        }
    }

    suspend fun switchServerOrUser() {
        preferenceStorage.remove(CURRENT_SERVER_ID_KEY)
        preferenceStorage.remove(CURRENT_USER_ID_KEY)
        _current.value = null
        _currentUserDto.value = null
    }

    suspend fun updateUserAuth(
        user: JellyfinUser,
        pin: String?,
        requireLogin: Boolean,
    ) {
        val newUser = user.copy(pin = pin, requireLogin = requireLogin)
        val updatedUser = withContext(ioDispatcher) { serverDao.addOrUpdateUser(newUser) }
        val cur = current.value
        if (cur?.user?.id == updatedUser.id && cur.server?.id == user.serverId) {
            // Updating current user, so push out the change
            current.value?.let {
                val newCurrent = it.copy(user = updatedUser)
                _current.value = newCurrent
            }
        }
    }

    suspend fun authorizeQuickConnect(code: String): Boolean =
        withContext(ioDispatcher) {
            val userId = current.value?.user?.id
            if (userId == null) {
                Log.e("No user logged in for Quick Connect authorization")
                throw IllegalStateException("Must be logged in to authorize Quick Connect")
            }
            val response = apiClient.quickConnectApi.authorizeQuickConnect(code, userId)
            response.content
        }

    /**
     * Update [currentUserDto] by querying the server
     */
    suspend fun updateUserDto() {
        val userDto by apiClient.userApi.getCurrentUser()
        _currentUserDto.update {
            if (it?.id == userDto.id && currentUser?.id == userDto.id) userDto else it
        }
    }
}
