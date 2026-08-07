package com.github.damontecres.wholphin.desktop.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.desktop.services.HomeSettingsService
import com.github.damontecres.wholphin.desktop.services.NavigationManager
import com.github.damontecres.wholphin.desktop.ui.nav.toDestination
import com.github.damontecres.wholphin.desktop.util.DesktopViewModel
import com.github.damontecres.wholphin.desktop.util.launchDefault
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val loadingState: LoadingState = LoadingState.Pending,
    val refreshState: LoadingState = LoadingState.Pending,
    val homeRows: List<HomeRowLoadingState> = emptyList(),
    val libraryNames: Map<UUID, String> = emptyMap(),
) {
    companion object {
        val EMPTY = HomeState()
    }
}

class HomeViewModel(
    private val serverRepository: ServerRepository,
    private val homeSettingsService: HomeSettingsService,
    private val navigationManager: NavigationManager,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(HomeState.EMPTY)
    val state: StateFlow<HomeState> = _state

    private var initialised by mutableStateOf(false)

    fun init() {
        if (initialised) return
        initialised = true
        viewModelScope.launchDefault { load() }
    }

    fun reload() {
        _state.update { it.copy(refreshState = LoadingState.Loading) }
        viewModelScope.launchDefault { load() }
    }

    private suspend fun load() {
        val user = serverRepository.current.value?.user ?: return
        _state.update { it.copy(loadingState = LoadingState.Loading) }
        try {
            val (rows, names) = homeSettingsService.getHomeRows(user.id)
            _state.update { it.copy(loadingState = LoadingState.Success, libraryNames = names) }
            rows.forEach { row ->
                viewModelScope.launch { fetchRow(user.id, row) }
            }
        } catch (ex: Exception) {
            _state.update {
                it.copy(
                    loadingState = LoadingState.Error(exception = ex),
                    refreshState = LoadingState.Error(exception = ex),
                )
            }
        }
    }

    private suspend fun fetchRow(
        userId: UUID,
        row: HomeRowConfig,
    ) {
        val title = rowTitle(row)
        _state.update { state ->
            state.copy(homeRows = state.homeRows + HomeRowLoadingState.Loading(title))
        }
        try {
            val items = homeSettingsService.fetchRow(userId, row)
            _state.update { state ->
                val rows =
                    state.homeRows.map { rowState ->
                        if (rowState is HomeRowLoadingState.Loading && rowState.title == title) {
                            HomeRowLoadingState.Success(
                                title = title,
                                items = items,
                                viewOptions = row.viewOptions,
                                rowType = row,
                                showViewMore = items.size >= ROW_LIMIT,
                            )
                        } else {
                            rowState
                        }
                    }
                state.copy(
                    homeRows = rows,
                    refreshState = LoadingState.Success,
                )
            }
        } catch (ex: Exception) {
            _state.update { state ->
                val rows =
                    state.homeRows.map { rowState ->
                        if (rowState is HomeRowLoadingState.Loading && rowState.title == title) {
                            HomeRowLoadingState.Error(title = title, message = ex.localizedMessage, exception = ex)
                        } else {
                            rowState
                        }
                    }
                state.copy(homeRows = rows)
            }
        }
    }

    private fun rowTitle(row: HomeRowConfig): String =
        when (row) {
            is HomeRowConfig.ContinueWatching -> "Continue Watching"
            is HomeRowConfig.NextUp -> "Next Up"
            is HomeRowConfig.RecentlyAdded -> "Recently Added" + _state.value.libraryNames[row.parentId]?.let { " in $it" }.orEmpty()
            is HomeRowConfig.ByParent -> _state.value.libraryNames[row.parentId] ?: "Library"
            else -> "Browse"
        }

    fun onItemClick(item: BaseItem) {
        navigationManager.navigateTo(item.toDestination())
    }

    fun onViewMore(row: HomeRowConfig, title: String) {
        navigationManager.navigateTo(
            com.github.damontecres.wholphin.desktop.ui.nav.Destination.MoreHomeRow(title, row),
        )
    }

    private companion object {
        const val ROW_LIMIT = 15
    }
}
