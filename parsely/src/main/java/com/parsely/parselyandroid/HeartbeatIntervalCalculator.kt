package com.parsely.parselyandroid

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal open class HeartbeatIntervalCalculator(private val clock: Clock) {

    open fun calculate(startTime: Duration): Long {
        val nowDuration = clock.now

        val totalTrackedTime = nowDuration - startTime
        val totalWithOffset = totalTrackedTime + OFFSET_MATCHING_BASE_INTERVAL
        val newInterval = totalWithOffset * BACKOFF_PROPORTION
        val clampedNewInterval = minOf(MAX_TIME_BETWEEN_HEARTBEATS, newInterval)
        return clampedNewInterval.inWholeMilliseconds
    }

    companion object {
        const val BACKOFF_PROPORTION = 0.3
        val OFFSET_MATCHING_BASE_INTERVAL = 35.seconds
        val MAX_TIME_BETWEEN_HEARTBEATS = 15.minutes
    }
}
