package com.github.damontecres.wholphin.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

/**
 * Logs uncaught coroutine exceptions. On Android [autoToast] additionally shows a toast;
 * on desktop it is a no-op (an in-app snackbar can be wired up later).
 */
class ExceptionHandler(
    @Suppress("unused") private val autoToast: Boolean = false,
) : CoroutineExceptionHandler {
    override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler

    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        if (exception is CancellationException) {
            return
        }
        Log.e(exception, "Uncaught exception")
    }
}
