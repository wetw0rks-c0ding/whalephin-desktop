package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Replaces Android's WorkManager for desktop. Runs periodic background tasks
 * using coroutine delay loops. Supports configurable interval, initial delay,
 * and a [shouldRun] predicate (e.g. network availability).
 *
 * Call [start] to begin the periodic loop; call [stop] to cancel.
 */
class BackgroundTaskScheduler(
    private val scope: CoroutineScope,
    private val interval: Duration,
    private val initialDelay: Duration = Duration.ZERO,
    private val shouldRun: suspend () -> Boolean = { true },
    private val task: suspend () -> Unit,
) {
    init {
        require(interval > Duration.ZERO) {
            "interval must be positive, was $interval"
        }
    }

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(initialDelay)
            while (isActive) {
                if (shouldRun()) {
                    try {
                        task()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e // preserve cancellation
                    } catch (_: Exception) {
                        // Logged upstream; continue the loop.
                    }
                }
                delay(interval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
