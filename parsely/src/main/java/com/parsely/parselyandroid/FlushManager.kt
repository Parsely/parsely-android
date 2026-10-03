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
internal interface FlushManager {
    fun start()
    fun stop()
    val isRunning: Boolean
    val intervalMillis: Long
}

internal class ParselyFlushManager(
    private val onFlush: () -> Unit,
    override val intervalMillis: Long,
    private val coroutineScope: CoroutineScope,
    private val jitter: () -> Double = { TimerJitter.between(MIN_JITTER, MAX_JITTER) },
) : FlushManager {
    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return

        job = coroutineScope.launch {
            while (isActive) {
                delay((intervalMillis * jitter()).toLong())
                onFlush.invoke()
            }
        }
    }

    override fun stop() {
        job?.cancel()
    }

    override val isRunning: Boolean
        get() = job?.isActive ?: false

    internal companion object {
        // Each tick waits 80-120% of the interval, so apps opened together stop flushing in step.
        const val MIN_JITTER = 0.8
        const val MAX_JITTER = 1.2
    }
}
