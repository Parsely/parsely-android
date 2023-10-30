package com.parsely.parselyandroid

import java.util.Timer
import java.util.TimerTask

/**
 * Manager for the event flush timer.
 *
 *
 * Handles stopping and starting the flush timer. The flush timer
 * controls how often we send events to Parse.ly servers.
 */
internal class FlushManager(
    private val parselyTracker: ParselyTracker,
    private val parentTimer: Timer,
    @JvmField val intervalMillis: Long
) {
    private var runningTask: TimerTask? = null
    fun start() {
        if (runningTask != null) {
            return
        }
        runningTask = object : TimerTask() {
            override fun run() {
                parselyTracker.flushEvents()
            }
        }
        parentTimer.scheduleAtFixedRate(runningTask, intervalMillis, intervalMillis)
    }

    fun stop(): Boolean {
        return if (runningTask == null) {
            false
        } else {
            val output = runningTask!!.cancel()
            runningTask = null
            output
        }
    }

    val isRunning: Boolean
        get() = runningTask != null
}