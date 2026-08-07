package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions

/**
 * Generic state for loading something from the API
 */
sealed interface LoadingState {
    data object Pending : LoadingState

    data object Loading : LoadingState

    data object Success : LoadingState

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : LoadingState {
        constructor(exception: Throwable) : this(null, exception)

        val localizedMessage: String =
            listOfNotNull(message, exception?.localizedMessage).joinToString(" - ")
    }
}

/**
 * A generic state for loading some data from the API
 */
sealed interface DataLoadingState<out T> {
    data object Pending : DataLoadingState<Nothing>

    data object Loading : DataLoadingState<Nothing>

    data class Success<T>(val data: T) : DataLoadingState<T>

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : DataLoadingState<Nothing>

    companion object {
        fun <T> of(result: Result<T>): DataLoadingState<T> =
            result.fold(
                onSuccess = { Success(it) },
                onFailure = { Error(it.message, it) },
            )
    }
}

/**
 * The loading state of a single row on the home page
 */
sealed interface HomeRowLoadingState {
    val title: String

    data class Pending(override val title: String) : HomeRowLoadingState

    data class Loading(override val title: String) : HomeRowLoadingState

    data class Success(
        override val title: String,
        val items: List<BaseItem?>,
        val viewOptions: HomeRowViewOptions,
        val rowType: HomeRowConfig?,
        val showViewMore: Boolean,
    ) : HomeRowLoadingState

    data class Error(
        override val title: String,
        val message: String? = null,
        val exception: Throwable? = null,
    ) : HomeRowLoadingState
}
