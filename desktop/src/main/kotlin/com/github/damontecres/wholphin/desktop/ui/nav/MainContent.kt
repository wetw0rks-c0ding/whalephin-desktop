package com.github.damontecres.wholphin.desktop.ui.nav

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.desktop.services.HomeSettingsService
import com.github.damontecres.wholphin.desktop.services.Library
import com.github.damontecres.wholphin.desktop.services.NavigationManager
import com.github.damontecres.wholphin.desktop.ui.components.LocalImageUrlService
import com.github.damontecres.wholphin.desktop.util.launchDefault
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.util.Log
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The post-login app shell: nav drawer + routed content. Desktop equivalent of the
 * app's `MainContent` + `ApplicationContent`.
 */
@Composable
fun MainContent(
    modifier: Modifier = Modifier,
) {
    val serverRepository = koinInject<ServerRepository>()
    val navigationManager = koinInject<NavigationManager>()
    val homeSettingsService = koinInject<HomeSettingsService>()
    val imageUrlService = koinInject<ImageUrlService>()
    val scope = rememberCoroutineScope()

    val current by serverRepository.current.collectAsState()
    val libraries = remember { mutableListOf<Library>() }
    var librariesState by remember { mutableStateOf(libraries.toList()) }

    LaunchedEffect(current?.server?.id, current?.user?.id) {
        librariesState = emptyList()
        val user = current?.user ?: return@LaunchedEffect
        runCatching { homeSettingsService.getLibraries(user.id) }
            .onSuccess { librariesState = it }
            .onFailure {
                librariesState = emptyList()
                Log.e(it, "Failed to load libraries for ${user.id}")
            }
    }

    // Highlight the drawer entry matching the current destination.
    val currentDestination = navigationManager.backStack.lastOrNull()
    var selectedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(currentDestination, librariesState) {
        selectedIndex =
            when (currentDestination) {
                is Destination.Home -> 0
                is Destination.Search -> 1
                is Destination.Favorites -> 2
                is Destination.MediaItem ->
                    librariesState
                        .indexOfFirst { it.id == currentDestination.itemId }
                        .takeIf { it >= 0 }
                        ?.plus(2)
                        ?: -1

                else -> -1
            }
    }

    val isFullScreen = currentDestination?.fullScreen == true
    CompositionLocalProvider(LocalImageUrlService provides imageUrlService) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (isFullScreen) {
                DestinationContent(
                    destination = navigationManager.backStack.last(),
                    onBack = { navigationManager.goBack() },
                    onItemClick = { item -> navigationManager.navigateTo(item.toDestination()) },
                    onPlay = { item ->
                        current?.let { cu ->
                            val dest = Destination.Playback(item.id, item.type, item.resumeMs)
                            navigationManager.navigateTo(dest)
                        }
                    },
                    onViewMore = { row, title -> navigationManager.navigateTo(Destination.MoreHomeRow(title, row, 0)) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.width(280.dp).fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        NavDrawer(
                            libraries = librariesState,
                            selectedIndex = selectedIndex,
                            user = current?.user?.name.orEmpty(),
                            server = current?.server?.name.orEmpty(),
                            onHome = { navigationManager.goToHome() },
                            onSearch = { navigationManager.navigateToFromDrawer(Destination.Search()) },
                            onFavorites = { navigationManager.navigateToFromDrawer(Destination.Favorites) },
                            onSettings = { navigationManager.navigateToFromDrawer(Destination.Settings) },
                            onLibrary = { index ->
                                val library = librariesState.getOrNull(index) ?: return@NavDrawer
                                navigationManager.navigateToFromDrawer(
                                    Destination.MediaItem(
                                        itemId = library.id,
                                        type = org.jellyfin.sdk.model.api.BaseItemKind.COLLECTION_FOLDER,
                                        collectionType = library.collectionType,
                                    ),
                                )
                            },
                            onSignOut = {
                                scope.launchDefault {
                                    current?.user?.let { serverRepository.removeUser(it) }
                                }
                            },
                        )
                    }
                    DestinationContent(
                        destination = navigationManager.backStack.last(),
                        onBack = { navigationManager.goBack() },
                        onItemClick = { item -> navigationManager.navigateTo(item.toDestination()) },
                        onPlay = { item ->
                        current?.let { cu ->
                            val dest = Destination.Playback(item.id, item.type, item.resumeMs)
                            navigationManager.navigateTo(dest)
                        }
                    },
                        onViewMore = { row, title -> navigationManager.navigateTo(Destination.MoreHomeRow(title, row, 0)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
