package com.github.damontecres.wholphin.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import coil3.ImageLoader
import kotlinx.coroutines.launch
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.desktop.services.SetupDestination
import com.github.damontecres.wholphin.desktop.services.SetupNavigationManager
import com.github.damontecres.wholphin.desktop.ui.nav.MainContent
import com.github.damontecres.wholphin.desktop.ui.setup.PinEntryContent
import com.github.damontecres.wholphin.desktop.ui.setup.ServerListContent
import com.github.damontecres.wholphin.desktop.ui.setup.UserListContent
import com.github.damontecres.wholphin.desktop.ui.setup.rememberSwitchServerViewModel
import com.github.damontecres.wholphin.desktop.ui.setup.rememberSwitchUserViewModel
import com.github.damontecres.wholphin.services.PreferenceStorage
import com.github.damontecres.wholphin.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import java.util.UUID

private val DarkColors = darkColorScheme()

@Composable
fun WholphinApp() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }
    MaterialTheme(colorScheme = DarkColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val navigationManager = koinInject<SetupNavigationManager>()
            var authError by remember { mutableStateOf<String?>(null) }
            val repo = koinInject<ServerRepository>()
            val scope = rememberCoroutineScope()
            val destination = navigationManager.backStack.firstOrNull() ?: SetupDestination.Loading
            when (destination) {
                SetupDestination.Loading -> StartupScreen(navigationManager)
                SetupDestination.ServerList -> {
                    val vm = rememberSwitchServerViewModel()
                    ServerListContent(
                        viewModel = vm,
                        onServerClick = { server ->
                            navigationManager.navigateTo(SetupDestination.UserList(server))
                        },
                    )
                }
                is SetupDestination.UserList -> {
                    val vm = rememberSwitchUserViewModel(destination.server)
                    UserListContent(viewModel = vm, server = destination.server)
                }
                is SetupDestination.PinEntry -> {
                    PinEntryContent(
                        current = destination.current,
                        authError = authError,
                        onSuccess = {
                            // Authenticate the user before transitioning to app content.
                            // Navigate only after changeUser succeeds.
                            scope.launch {
                                try {
                                    repo.changeUser(destination.current.server, destination.current.user)
                                    navigationManager.navigateTo(SetupDestination.AppContent(destination.current))
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e(e, "PIN auth failed")
                                    authError = "Authentication failed: ${e.message ?: "Unknown error"}"
                                }
                            }
                        },
                        onCancel = {
                            navigationManager.navigateTo(SetupDestination.ServerList)
                        },
                    )
                }
                is SetupDestination.AppContent -> MainContent()
            }
        }
    }
}

/**
 * Restores the last-used session (or routes to the server list) at app start.
 */
@Composable
private fun StartupScreen(navigationManager: SetupNavigationManager) {
    val serverRepository = koinInject<ServerRepository>()
    val preferenceStorage = koinInject<PreferenceStorage>()

    LaunchedEffect(Unit) {
        try {
            val serverId =
                preferenceStorage
                    .get(ServerRepository.CURRENT_SERVER_ID_KEY, "")
                    .first()
                    .takeIf { it.isNotEmpty() }
            val userId =
                preferenceStorage
                    .get(ServerRepository.CURRENT_USER_ID_KEY, "")
                    .first()
                    .takeIf { it.isNotEmpty() }
            val current =
                if (serverId != null && userId != null) {
                    serverRepository.restoreSession(
                        serverId.toUUIDFromServerString(),
                        userId.toUUIDFromServerString(),
                    )
                } else {
                    null
                }
            when {
                current == null -> navigationManager.navigateTo(SetupDestination.ServerList)
                current.user.pin != null -> navigationManager.navigateTo(SetupDestination.PinEntry(current))
                else -> navigationManager.navigateTo(SetupDestination.AppContent(current))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Log.e(ex, "Error restoring session")
            navigationManager.navigateTo(SetupDestination.ServerList)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Restoring session...")
        }
    }
}

/**
 * Reverses [com.github.damontecres.wholphin.util.toServerString]: re-inserts the dashes
 * of the canonical UUID form so the stored string can be parsed back into a [UUID].
 */
private fun String.toUUIDFromServerString(): UUID? {
    if (length != 32) return null
    return try {
        UUID.fromString(
            "${substring(0, 8)}-${substring(8, 12)}-${substring(12, 16)}-${substring(16, 20)}-${substring(20)}",
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
