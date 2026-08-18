package com.github.damontecres.wholphin.desktop.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.util.DataLoadingState
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject

/**
 * Search page: a query field plus one row of results per item type.
 */
@Composable
fun SearchPage(
    initialQuery: String,
    onItemClick: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val api = koinInject<ApiClient>()
    val viewModel = remember(initialQuery) { SearchViewModel(api) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()

    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    LaunchedEffect(query) { viewModel.search(query) }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search movies, series, episodes…") },
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            singleLine = true,
        )
        if (query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Search your media library", style = MaterialTheme.typography.titleLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(SearchType.entries) { type ->
                    val result = state.results[type]
                    when (result) {
                        null, is DataLoadingState.Pending -> Unit
                        is DataLoadingState.Loading ->
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }

                        is DataLoadingState.Error ->
                            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                                Text(
                                    text = "${type.label} search failed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                        is DataLoadingState.Success -> {
                            if (result.data.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(bottom = 24.dp),
                                ) {
                                    Text(
                                        text = type.label,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        result.data.forEachIndexed { index, item ->
                                            Text(
                                                text = item?.name ?: "Item $index",
                                                modifier = Modifier.padding(8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
