package com.github.damontecres.wholphin.desktop.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.desktop.util.DesktopViewModel
import com.github.damontecres.wholphin.desktop.util.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class HomeRowState(
    val loadingState: LoadingState = LoadingState.Pending,
    val items: List<BaseItem?> = emptyList(),
) {
    companion object {
        val EMPTY = HomeRowState()
    }
}

/**
 * Fetches the full contents of a home row for the "View more" page.
 */
class HomeRowService(
    private val serverRepository: ServerRepository,
    private val homeSettingsService: HomeSettingsService,
) : DesktopViewModel() {
    private val _state = MutableStateFlow(HomeRowState.EMPTY)
    val state: StateFlow<HomeRowState> = _state

    fun load(config: HomeRowConfig) {
        _state.update { it.copy(loadingState = LoadingState.Loading) }
        viewModelScope.launchIO {
            val user = serverRepository.current.value?.user?.id ?: return@launchIO
            try {
                val items = homeSettingsService.fetchRow(user, config, limit = FULL_LIMIT)
                _state.update { it.copy(loadingState = LoadingState.Success, items = items) }
            } catch (ex: Exception) {
                _state.update { it.copy(loadingState = LoadingState.Error(exception = ex)) }
            }
        }
    }

    companion object {
        const val FULL_LIMIT = 100
    }
}
