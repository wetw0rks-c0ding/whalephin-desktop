package com.github.damontecres.wholphin.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

/**
 * Logs uncaught coroutine exceptions. The [autoToast] parameter is reserved for
 * platform-specific notification wiring (Android toast / desktop snackbar);
 * currently a no-op on all platforms until a notification channel is integrated.
 */
class ExceptionHandler(
    @Suppress("unused") autoToast: Boolean = false,
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
