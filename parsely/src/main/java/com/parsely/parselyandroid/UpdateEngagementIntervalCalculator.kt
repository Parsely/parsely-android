package com.parsely.parselyandroid

import java.util.Calendar

internal open class UpdateEngagementIntervalCalculator(private val clock: Clock) {

    open fun updateLatestInterval(startTime: Calendar): Long {
        val totalTrackedTime = (clock.now - startTime.time.time) / 1000
        val totalWithOffset = (totalTrackedTime + OFFSET_MATCHING_BASE_INTERVAL).toDouble()
        val newInterval = totalWithOffset * BACKOFF_PROPORTION
        val clampedNewInterval =
            Math.min(MAX_TIME_BETWEEN_HEARTBEATS.toDouble(), newInterval).toLong()
        return clampedNewInterval * 1000
    }

    companion object {
        const val MAX_TIME_BETWEEN_HEARTBEATS = (60 * 60).toLong()
        const val OFFSET_MATCHING_BASE_INTERVAL: Long = 35
        const val BACKOFF_PROPORTION = 0.3
    }
}