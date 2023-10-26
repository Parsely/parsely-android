package com.parsely.parselyandroid

import java.util.Calendar
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal open class HeartbeatIntervalCalculator(private val clock: Clock) {

    open fun calculate(startTime: Calendar): Long {
        val startTimeDuration = startTime.time.time.milliseconds
        val nowDuration = clock.now

        val totalTrackedTime = nowDuration - startTimeDuration
        val totalWithOffset = totalTrackedTime + OFFSET_MATCHING_BASE_INTERVAL
        val newInterval = totalWithOffset * BACKOFF_PROPORTION
        val clampedNewInterval = minOf(MAX_TIME_BETWEEN_HEARTBEATS, newInterval)
        return clampedNewInterval.inWholeMilliseconds
    }

    companion object {
        const val BACKOFF_PROPORTION = 0.3
        val OFFSET_MATCHING_BASE_INTERVAL = 35.seconds
        val MAX_TIME_BETWEEN_HEARTBEATS = 1.hours
    }
}