package com.github.damontecres.wholphin.desktop.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.desktop.services.HomeSettingsService
import com.github.damontecres.wholphin.desktop.services.NavigationManager
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import org.koin.compose.koinInject

@Composable
fun rememberHomeViewModel(): HomeViewModel {
    val serverRepository = koinInject<ServerRepository>()
    val homeSettingsService = koinInject<HomeSettingsService>()
    val navigationManager = koinInject<NavigationManager>()
    val vm =
        androidx.compose.runtime.remember {
            HomeViewModel(
                serverRepository = serverRepository,
                homeSettingsService = homeSettingsService,
                navigationManager = navigationManager,
            )
        }
    androidx.compose.runtime.DisposableEffect(vm) {
        onDispose { vm.clear() }
    }
    return vm
}

/**
 * The home page: a vertical list of media rows (Continue Watching, Next Up, per-library
 * Recently Added and library browse rows).
 */
@Composable
fun HomePage(
    onItemClick: (BaseItem) -> Unit,
    onViewMore: (HomeRowConfig, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = rememberHomeViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.init() }

    when (val loading = state.loadingState) {
        com.github.damontecres.wholphin.util.LoadingState.Pending,
        com.github.damontecres.wholphin.util.LoadingState.Loading,
        -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is com.github.damontecres.wholphin.util.LoadingState.Error -> {
            Column(modifier = modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Unable to load home", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(loading.localizedMessage, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.reload() }) {
                    Text("Retry")
                }
            }
        }

        com.github.damontecres.wholphin.util.LoadingState.Success -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                item { Spacer(Modifier.height(24.dp)) }
                items(state.homeRows) { row ->
                    HomeRowContent(
                        row = row,
                        onItemClick = onItemClick,
                        onViewMore = onViewMore,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeRowContent(
    row: HomeRowLoadingState,
    onItemClick: (BaseItem) -> Unit,
    onViewMore: (HomeRowConfig, String) -> Unit,
) {
    when (row) {
        is HomeRowLoadingState.Pending,
        is HomeRowLoadingState.Loading,
        -> Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        is HomeRowLoadingState.Success ->
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleLarge,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(row.items.size) { index ->
                        Text(
                            text = row.items[index]?.name ?: "Item $index",
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
                onViewMore = row.rowType?.let { { onViewMore(it, row.title) } },
            )

        is HomeRowLoadingState.Error ->
            Text(
                text = "Failed to load ${row.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
    }
}
