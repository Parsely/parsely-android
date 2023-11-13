package com.parsely.parselyandroid

import java.util.Calendar
import java.util.TimeZone
import java.util.Timer
import java.util.TimerTask

/**
 * Engagement manager for article and video engagement.
 *
 *
 * Implemented to handle its own queuing of future executions to accomplish
 * two things:
 *
 *
 * 1. Flushing any engaged time before canceling.
 * 2. Progressive backoff for long engagements to save data.
 */
internal class EngagementManager(
    private val parselyTracker: ParselyTracker,
    private val parentTimer: Timer,
    private var latestDelayMillis: Long,
    var baseEvent: Map<String, Any>,
    private val intervalCalculator: HeartbeatIntervalCalculator
) {
    var isRunning = false
        private set
    private var waitingTimerTask: TimerTask? = null
    private var totalTime: Long = 0
    private var startTime: Calendar

    init {
        startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }

    fun start() {
        scheduleNextExecution(latestDelayMillis)
        isRunning = true
        startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }

    fun stop() {
        waitingTimerTask!!.cancel()
        isRunning = false
    }

    fun isSameVideo(url: String, urlRef: String, metadata: ParselyVideoMetadata): Boolean {
        val baseMetadata = baseEvent["metadata"] as Map<String, Any>?
        return baseEvent["url"] == url && baseEvent["urlref"] == urlRef && baseMetadata!!["link"] == metadata.link && baseMetadata["duration"] as Int == metadata.durationSeconds
    }

    private fun scheduleNextExecution(delay: Long) {
        val task: TimerTask = object : TimerTask() {
            override fun run() {
                doEnqueue(scheduledExecutionTime())
                latestDelayMillis = intervalCalculator.calculate(startTime)
                scheduleNextExecution(latestDelayMillis)
            }

            override fun cancel(): Boolean {
                val output = super.cancel()
                // Only enqueue when we actually canceled something. If output is false then
                // this has already been canceled.
                if (output) {
                    doEnqueue(scheduledExecutionTime())
                }
                return output
            }
        }
        latestDelayMillis = delay
        parentTimer.schedule(task, delay)
        waitingTimerTask = task
    }

    private fun doEnqueue(scheduledExecutionTime: Long) {
        // Create a copy of the base event to enqueue
        val event: MutableMap<String, Any> = HashMap(
            baseEvent
        )
        ParselyTracker.PLog(String.format("Enqueuing %s event.", event["action"]))

        // Update `ts` for the event since it's happening right now.
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val baseEventData = (event["data"] as Map<String, Any>?)!!
        val data: MutableMap<String, Any> = HashMap(baseEventData)
        data["ts"] = now.timeInMillis
        event["data"] = data

        // Adjust inc by execution time in case we're late or early.
        val executionDiff = System.currentTimeMillis() - scheduledExecutionTime
        val inc = latestDelayMillis + executionDiff
        totalTime += inc
        event["inc"] = inc / 1000
        event["tt"] = totalTime
        parselyTracker.enqueueEvent(event)
    }

    val intervalMillis: Double
        get() = latestDelayMillis.toDouble()
}
