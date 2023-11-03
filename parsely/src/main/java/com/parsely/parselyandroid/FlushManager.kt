package com.parsely.parselyandroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manager for the event flush timer.
 *
 *
 * Handles stopping and starting the flush timer. The flush timer
 * controls how often we send events to Parse.ly servers.
 */
internal class FlushManager(
    private val parselyTracker: ParselyTracker,
    val intervalMillis: Long,
    private val coroutineScope: CoroutineScope
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        job = coroutineScope.launch {
            while (isActive) {
                delay(intervalMillis)
                parselyTracker.flushEvents()
            }
        }
    }

    fun stop() = job?.cancel()

    val isRunning: Boolean
        get() = job?.isActive ?: false
}
