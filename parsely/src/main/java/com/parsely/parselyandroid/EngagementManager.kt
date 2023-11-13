package com.parsely.parselyandroid

import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private var latestDelayMillis: Long,
    var baseEvent: Map<String, Any>,
    private val intervalCalculator: HeartbeatIntervalCalculator,
    private val coroutineScope: CoroutineScope,
    private val clock: Clock,
) {
    var isRunning = false
        private set
    private var job: Job? = null
    private var totalTime: Long = 0
    private var startTime: Calendar
    private var nextScheduledExecution: Long = 0

    init {
        startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    }

    fun start() {
        isRunning = true
        startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = clock.now.inWholeMilliseconds
        }
        job = coroutineScope.launch {
            while (isActive) {
                latestDelayMillis = intervalCalculator.calculate(startTime)
                nextScheduledExecution = clock.now.inWholeMilliseconds + latestDelayMillis
                delay(latestDelayMillis)
                doEnqueue(clock.now.inWholeMilliseconds)
            }
        }
    }

    fun stop() {
        job?.let {
            it.cancel()
            doEnqueue(nextScheduledExecution)
        }
        isRunning = false
    }

    fun isSameVideo(url: String, urlRef: String, metadata: ParselyVideoMetadata): Boolean {
        val baseMetadata = baseEvent["metadata"] as Map<String, Any>?
        return baseEvent["url"] == url && baseEvent["urlref"] == urlRef && baseMetadata!!["link"] == metadata.link && baseMetadata["duration"] as Int == metadata.durationSeconds
    }

    private fun doEnqueue(scheduledExecutionTime: Long) {
        // Create a copy of the base event to enqueue
        val event: MutableMap<String, Any> = HashMap(
            baseEvent
        )
        ParselyTracker.PLog(String.format("Enqueuing %s event.", event["action"]))

        // Update `ts` for the event since it's happening right now.
        val baseEventData = (event["data"] as Map<String, Any>?)!!
        val data: MutableMap<String, Any> = HashMap(baseEventData)
        data["ts"] = clock.now.inWholeMilliseconds
        event["data"] = data

        // Adjust inc by execution time in case we're late or early.
        val executionDiff = clock.now.inWholeMilliseconds - scheduledExecutionTime
        val inc = latestDelayMillis + executionDiff
        totalTime += inc
        event["inc"] = inc / 1000
        event["tt"] = totalTime
        parselyTracker.enqueueEvent(event)
    }

    val intervalMillis: Double
        get() = latestDelayMillis.toDouble()
}
