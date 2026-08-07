package com.github.damontecres.wholphin.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Shared dispatchers for the app.
 *
 * [Dispatchers.Main] is provided by the platform: on desktop the Swing EDT via
 * kotlinx-coroutines-swing, on Android the main looper.
 */
object WholphinDispatchers {
    val Main: CoroutineDispatcher
        get() = Dispatchers.Main

    val IO: CoroutineDispatcher
        get() = Dispatchers.IO

    val Default: CoroutineDispatcher
        get() = Dispatchers.Default
}
