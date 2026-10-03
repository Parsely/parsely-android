package com.parsely.parselyandroid

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal open class HeartbeatIntervalCalculator(
    private val clock: Clock,
    private val jitter: () -> Double = { Random.nextDouble(MIN_JITTER, MAX_JITTER) },
) {

    open fun calculate(startTime: Duration): Long {
        val nowDuration = clock.now

        val totalTrackedTime = nowDuration - startTime
        val totalWithOffset = totalTrackedTime + OFFSET_MATCHING_BASE_INTERVAL
        val newInterval = totalWithOffset * BACKOFF_PROPORTION * jitter()
        val clampedNewInterval = minOf(MAX_TIME_BETWEEN_HEARTBEATS, newInterval)
        return clampedNewInterval.inWholeMilliseconds
    }

    companion object {
        const val BACKOFF_PROPORTION = 0.3
        val OFFSET_MATCHING_BASE_INTERVAL = 35.seconds
        val MAX_TIME_BETWEEN_HEARTBEATS = 15.minutes

        // Up to 10% either way, so sessions started together drift apart. The cap above still holds.
        const val MIN_JITTER = 0.9
        const val MAX_JITTER = 1.1
    }
}
