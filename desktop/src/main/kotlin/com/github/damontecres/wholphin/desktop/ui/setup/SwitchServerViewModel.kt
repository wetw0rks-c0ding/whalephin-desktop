package com.github.damontecres.wholphin.desktop.ui.setup

import com.github.damontecres.wholphin.data.ServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.desktop.services.SetupDestination
import com.github.damontecres.wholphin.desktop.services.SetupNavigationManager
import com.github.damontecres.wholphin.desktop.util.DesktopViewModel
import com.github.damontecres.wholphin.desktop.util.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.discovery.RecommendedServerInfoScore
import org.jellyfin.sdk.discovery.RecommendedServerIssue
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop port of the Android `SwitchServerViewModel` (Context/Toast/Timber removed).
 */
class SwitchServerViewModel(
    val jellyfin: Jellyfin,
    val serverRepository: ServerRepository,
    val serverDao: ServerDao,
    val navigationManager: SetupNavigationManager,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(SwitchServerState())
    val state: StateFlow<SwitchServerState> = _state

    fun clearAddServerState() {
        _state.update { it.copy(addServerState = LoadingState.Pending) }
    }

    fun init() {
        viewModelScope.launchIO {
            _state.update { SwitchServerState() }

            val allServers =
                serverDao
                    .getServers()
                    .map { it.server }
                    .sortedWith(compareBy<JellyfinServer> { it.name }.thenBy { it.url })
                    .map { ServerState(it) }
            _state.update {
                it.copy(
                    loading = LoadingState.Success,
                    servers = allServers,
                )
            }
            allServers.forEach { server ->
                internalTestServer(server.server)
            }
        }
    }

    private fun updateServerState(
        server: JellyfinServer,
        result: ServerConnectionStatus,
    ) {
        val supported =
            if (result is ServerConnectionStatus.Success) {
                val serverVersion =
                    result.systemInfo.version?.let { ServerVersion.fromString(it) }
                if (serverVersion == null || serverVersion < Jellyfin.minimumVersion) {
                    ServerVersionSupported.NOT_SUPPORTED
                } else {
                    ServerVersionSupported.SUPPORTED
                }
            } else {
                ServerVersionSupported.UNKNOWN
            }
        _state.update {
            val servers =
                it.servers.toMutableList().apply {
                    val index = indexOfFirst { it.server.id == server.id }
                    set(
                        index,
                        get(index).copy(
                            server = server,
                            status = result,
                            versionSupported = supported,
                        ),
                    )
                }
            it.copy(servers = servers)
        }
    }

    fun testServer(server: JellyfinServer) {
        updateServerState(server, ServerConnectionStatus.Pending)
        viewModelScope.launchIO {
            delay(500)
            internalTestServer(server)
        }
    }

    private suspend fun internalTestServer(server: JellyfinServer): ServerConnectionStatus {
        val result =
            try {
                val systemInfo =
                    jellyfin
                        .createApi(
                            server.url,
                            httpClientOptions =
                                HttpClientOptions(
                                    requestTimeout = 6.seconds,
                                    connectTimeout = 6.seconds,
                                    socketTimeout = 6.seconds,
                                ),
                        ).systemApi
                        .getPublicSystemInfo()
                        .content
                ServerConnectionStatus.Success(systemInfo)
            } catch (ex: Exception) {
                Log.w(ex, "Error checking server ${server.url}")
                ServerConnectionStatus.Error(ex.localizedMessage)
            }
        updateServerState(server, result)
        return result
    }

    fun switchServer(server: JellyfinServer) {
        viewModelScope.launchIO {
            updateServerState(server, ServerConnectionStatus.Pending)
            val result = internalTestServer(server)
            if (result is ServerConnectionStatus.Success) {
                val updatedServer =
                    server.copy(
                        name = result.systemInfo.serverName,
                        version = result.systemInfo.version,
                    )
                serverRepository.addAndChangeServer(updatedServer)
                navigationManager.navigateTo(SetupDestination.UserList(updatedServer))
            } else if (result is ServerConnectionStatus.Error) {
                _state.update { it.copy(addServerState = LoadingState.Error("Error connecting: ${result.message}")) }
            }
        }
    }

    fun addServer(inputUrl: String) {
        _state.update { it.copy(addServerState = LoadingState.Loading) }
        viewModelScope.launchIO {
            try {
                val scores =
                    jellyfin.discovery.getRecommendedServers(inputUrl).sortedBy { it.score }
                val bestServer =
                    scores.firstOrNull { it.score != RecommendedServerInfoScore.BAD }
                val serverInfo = bestServer?.systemInfo?.getOrNull()
                if (bestServer != null && serverInfo != null) {
                    val serverUrl = bestServer.address

                    val id = serverInfo.id?.toUUIDOrNull()

                    if (id != null && serverInfo.startupWizardCompleted == true) {
                        val server =
                            JellyfinServer(
                                id = id,
                                name = serverInfo.serverName,
                                url = serverUrl,
                                version = serverInfo.version,
                            )
                        serverRepository.addAndChangeServer(server)
                        _state.update { it.copy(addServerState = LoadingState.Success) }
                        navigationManager.navigateTo(SetupDestination.UserList(server))
                    } else {
                        _state.update { it.copy(addServerState = LoadingState.Error("Server returned invalid response")) }
                    }
                } else {
                    Log.w("Error connecting with $inputUrl: $scores")
                    // No good server candidate
                    val errors =
                        scores.joinToString("\n") {
                            val issues =
                                it.issues.firstOrNull()?.let { issue ->
                                    when (issue) {
                                        is RecommendedServerIssue.InvalidProductName,
                                        is RecommendedServerIssue.MissingSystemInfo,
                                        -> "Invalid server info"

                                        is RecommendedServerIssue.SecureConnectionFailed,
                                        is RecommendedServerIssue.ServerUnreachable,
                                        is RecommendedServerIssue.SlowResponse,
                                        -> "Unable to connect"

                                        RecommendedServerIssue.MissingVersion,
                                        is RecommendedServerIssue.OutdatedServerVersion,
                                        is RecommendedServerIssue.UnsupportedServerVersion,
                                        -> "Unsupported server version"
                                    }
                                }
                            "${it.address} - $issues"
                        }
                    val message = "Error, tried addresses:\n$errors"
                    _state.update { it.copy(addServerState = LoadingState.Error(message)) }
                }
            } catch (ex: Exception) {
                Log.w(ex, "Error creating API for $inputUrl")
                _state.update { it.copy(addServerState = LoadingState.Error(exception = ex)) }
            }
        }
    }

    fun removeServer(server: JellyfinServer) {
        viewModelScope.launchIO {
            serverRepository.removeServer(server)
            init()
        }
    }

    fun discoverServers() {
        viewModelScope.launchIO {
            jellyfin.discovery.discoverLocalServers().collect { server ->
                val jellyfinServer =
                    JellyfinServer(
                        server.id.toUUID(),
                        server.name,
                        server.address,
                        null,
                    )
                _state.update {
                    it.copy(
                        discoveredServers =
                            it.discoveredServers
                                .toMutableList()
                                .apply {
                                    add(jellyfinServer)
                                },
                    )
                }
            }
        }
    }
}

data class SwitchServerState(
    val loading: LoadingState = LoadingState.Pending,
    val servers: List<ServerState> = emptyList(),
    val discoveredServers: List<JellyfinServer> = emptyList(),
    val addServerState: LoadingState = LoadingState.Pending,
)

data class ServerState(
    val server: JellyfinServer,
    val status: ServerConnectionStatus = ServerConnectionStatus.Pending,
    val quickConnect: Boolean = false,
    val versionSupported: ServerVersionSupported = ServerVersionSupported.UNKNOWN,
)

enum class ServerVersionSupported {
    SUPPORTED,
    NOT_SUPPORTED,
    UNKNOWN,
}

sealed interface ServerConnectionStatus {
    data object Pending : ServerConnectionStatus

    data class Success(val systemInfo: PublicSystemInfo) : ServerConnectionStatus

    data class Error(val message: String?) : ServerConnectionStatus
}
