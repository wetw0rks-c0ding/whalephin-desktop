package com.github.damontecres.wholphin.desktop.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.ServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.CurrentUser
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.desktop.util.checkPin
import com.github.damontecres.wholphin.desktop.util.pinLengthFromHash
import com.github.damontecres.wholphin.desktop.services.SetupDestination
import com.github.damontecres.wholphin.desktop.services.SetupNavigationManager
import com.github.damontecres.wholphin.desktop.util.DesktopViewModel
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.koin.compose.koinInject

// ---------------------------------------------------------------------------
// ViewModel factories — construct the plain (non-Koin-managed) screen viewmodels,
// tied to the composition's lifecycle so background work dies with the screen.
// ---------------------------------------------------------------------------

@Composable
fun rememberSwitchServerViewModel(): SwitchServerViewModel {
    val jellyfin = koinInject<Jellyfin>()
    val serverRepository = koinInject<ServerRepository>()
    val serverDao = koinInject<ServerDao>()
    val nav = koinInject<SetupNavigationManager>()
    val vm = remember { SwitchServerViewModel(jellyfin, serverRepository, serverDao, nav) }
    return rememberViewModel(vm)
}

@Composable
fun rememberSwitchUserViewModel(server: JellyfinServer): SwitchUserViewModel {
    val jellyfin = koinInject<Jellyfin>()
    val serverRepository = koinInject<ServerRepository>()
    val serverDao = koinInject<ServerDao>()
    val nav = koinInject<SetupNavigationManager>()
    val vm =
        remember(server) {
            SwitchUserViewModel(
                jellyfin = jellyfin,
                serverRepository = serverRepository,
                serverDao = serverDao,
                setupNavigationManager = nav,
                server = server,
            )
        }
    return rememberViewModel(vm)
}

@Composable
private fun <T : DesktopViewModel> rememberViewModel(vm: T): T {
    androidx.compose.runtime.DisposableEffect(vm) {
        onDispose { vm.clear() }
    }
    return vm
}

// ---------------------------------------------------------------------------
// Server list / add server
// ---------------------------------------------------------------------------

@Composable
fun ServerListContent(
    viewModel: SwitchServerViewModel,
    onServerClick: (JellyfinServer) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.init() }

    var urlInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Select a server", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    viewModel.clearAddServerState()
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Server address, e.g. http://192.168.1.10:8096") },
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = urlInput.isNotBlank() && state.addServerState !is LoadingState.Loading,
                onClick = {
                    viewModel.addServer(urlInput.trim())
                },
            ) {
                Text("Add")
            }
        }
        Text(
            "Include the full base URL. For servers behind a reverse proxy add the path prefix, e.g. https://myserver.example/jellyfin",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val addState = state.addServerState) {
            is LoadingState.Error -> Text(addState.localizedMessage, color = MaterialTheme.colorScheme.error)
            LoadingState.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
            else -> {}
        }

        HorizontalDivider()

        Text("Saved servers", style = MaterialTheme.typography.titleMedium)
        if (state.loading is LoadingState.Loading || state.loading is LoadingState.Pending) {
            CircularProgressIndicator()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(state.servers, key = { it.server.id }) { serverState ->
                    ServerRow(
                        server = serverState.server,
                        statusText = when (val status = serverState.status) {
                            is ServerConnectionStatus.Success -> "Connected - ${status.systemInfo.serverName}"
                            is ServerConnectionStatus.Error -> "Unreachable: ${status.message ?: "unknown error"}"
                            else -> "Checking..."
                        },
                        versionSupported = serverState.versionSupported,
                        onClick = { onServerClick(serverState.server) },
                        onRemove = { viewModel.removeServer(serverState.server) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (state.discoveredServers.isNotEmpty()) {
            Text("Discovered on network", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.discoveredServers, key = { it.id }) { server ->
                    OutlinedButton(onClick = { onServerClick(server) }) {
                        Text(server.name ?: "Unknown")
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = {
                    scope.launch { viewModel.discoverServers() }
                },
            ) {
                Text("Discover local servers")
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: JellyfinServer,
    statusText: String,
    versionSupported: ServerVersionSupported,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(server.name ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                Text(server.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (versionSupported == ServerVersionSupported.NOT_SUPPORTED) {
                    Text("Unsupported server version", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// User list / login / quick connect
// ---------------------------------------------------------------------------

@Composable
fun UserListContent(
    viewModel: SwitchUserViewModel,
    server: JellyfinServer,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.init() }

    var showLogin by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var switchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Select a user", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "${server.name} - ${server.url}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.serverVersionSupported == ServerVersionSupported.NOT_SUPPORTED) {
            Text("Unsupported server version", color = MaterialTheme.colorScheme.error)
        }

        when (val switchState = state.switchUserState) {
            is LoadingState.Error -> Text(switchState.localizedMessage, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
        switchError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (state.loading is LoadingState.Loading || state.loading is LoadingState.Pending) {
            CircularProgressIndicator()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(state.users, key = { it.user.id }) { userAndImage ->
                    UserRow(
                        user = userAndImage.user,
                        onClick = {
                            scope.launch {
                                val error = viewModel.trySwitchUser(userAndImage.user).await()
                                switchError = error
                            }
                        },
                        onRemove = { viewModel.removeUser(userAndImage.user) },
                    )
                }
            }
        }

        if (showLogin) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = username.isNotBlank() && password.isNotEmpty(),
                    onClick = { viewModel.login(server, null, username, password) },
                ) {
                    Text("Login")
                }
                OutlinedButton(onClick = { showLogin = false }) { Text("Cancel") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showLogin = true }) {
                    Text("Login with username & password")
                }
                if (state.quickConnectEnabled) {
                    OutlinedButton(onClick = { viewModel.initiateQuickConnect(server, null) }) {
                        Text("Quick Connect")
                    }
                }
            }
            val quickConnectStatus = state.quickConnectStatus
            if (quickConnectStatus != null) {
                QuickConnectDialog(quickConnectStatus, onCancel = { viewModel.cancelQuickConnect() })
            }
        }
    }
}

@Composable
private fun UserRow(
    user: JellyfinUser,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(user.name?.firstOrNull()?.uppercase() ?: "?", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name ?: "", style = MaterialTheme.typography.titleMedium)
                if (user.pin != null) {
                    Text("PIN required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            OutlinedButton(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}

@Composable
private fun QuickConnectDialog(
    status: QuickConnectResult,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Quick Connect", style = MaterialTheme.typography.titleLarge)
            Text(
                "On your Jellyfin server, open the Quick Connect page and enter the code below.",
                textAlign = TextAlign.Center,
            )
            Text(
                status.code ?: "-----",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            if (status.authenticated) {
                Text("Authenticated! Switching user...", color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PIN entry
// ---------------------------------------------------------------------------

@Composable
fun PinEntryContent(
    current: CurrentUser,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    val pinLength = pinLengthFromHash(current.user.pin)
    var error by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    val char = event.utf16CodePoint.toChar()
                    if (char.isDigit() && entered.length < pinLength) {
                        entered += char.toString(); error = false; true
                    } else if (event.key == Key.Backspace && entered.isNotEmpty()) {
                        entered = entered.dropLast(1); error = false; true
                    } else false
                } else false
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Enter PIN for ${current.user.name}", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(pinLength) { index ->
                Box(
                    modifier =
                        Modifier.size(48.dp)
                            .background(
                                color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(entered.getOrNull(index)?.toString() ?: "•", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..9).forEach { digit ->
                PinButton(digit.toString(), enabled = entered.length < pinLength) {
                    entered += digit.toString()
                    error = false
                }
            }
            PinButton("0", enabled = entered.length < pinLength) { entered += "0"; error = false }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val storedPin = current.user.pin ?: ""
                    if (checkPin(entered, storedPin)) {
                        onSuccess()
                    } else {
                        error = true
                        entered = ""
                    }
                },
                enabled = entered.length == pinLength,
            ) {
                Text("OK")
            }
            OutlinedButton(
                onClick = {
                    entered = entered.dropLast(1)
                    error = false
                },
            ) {
                Text("Backspace")
            }
            OutlinedButton(onClick = onCancel) {
                Text("Switch user")
            }
        }
    }
}

@Composable
private fun PinButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}
