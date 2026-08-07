package com.github.damontecres.wholphin.desktop.util

import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Launches a coroutine on [WholphinDispatchers.IO] with an [ExceptionHandler]
 */
fun CoroutineScope.launchIO(
    context: CoroutineContext = ExceptionHandler(),
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context = WholphinDispatchers.IO + context, start = start, block = block)

/**
 * Launches a coroutine on [WholphinDispatchers.Default] with an [ExceptionHandler]
 */
fun CoroutineScope.launchDefault(
    context: CoroutineContext = ExceptionHandler(),
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context = WholphinDispatchers.Default + context, start = start, block = block)

/**
 * Desktop equivalent of `viewModelScope`: a scope tied to a screen, cancelled when the
 * owning composable leaves composition via [clear].
 */
abstract class DesktopViewModel {
    val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun clear() {
        viewModelScope.cancel()
    }
}
