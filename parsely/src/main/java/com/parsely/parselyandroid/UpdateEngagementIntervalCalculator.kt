package com.parsely.parselyandroid

import java.util.Calendar
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal open class UpdateEngagementIntervalCalculator(private val clock: Clock) {

    open fun updateLatestInterval(startTime: Calendar): Long {
        val startTimeDuration = startTime.time.time.milliseconds
        val nowDuration = clock.now

        val totalTrackedTime = nowDuration - startTimeDuration
        val totalWithOffset = totalTrackedTime + offset
        val newInterval = totalWithOffset * BACKOFF_PROPORTION
        val clampedNewInterval = minOf(maxTimeBetweenHeartbeats, newInterval)
        return clampedNewInterval.inWholeMilliseconds
    }

    companion object {
        const val MAX_TIME_BETWEEN_HEARTBEATS = (60 * 60).toLong()
        const val OFFSET_MATCHING_BASE_INTERVAL: Long = 35
        const val BACKOFF_PROPORTION = 0.3

        val offset = 35.seconds
        val maxTimeBetweenHeartbeats = 1.hours
    }
}