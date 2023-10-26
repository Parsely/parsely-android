package com.parsely.parselyandroid

import com.parsely.parselyandroid.UpdateEngagementIntervalCalculator.Companion.BACKOFF_PROPORTION
import com.parsely.parselyandroid.UpdateEngagementIntervalCalculator.Companion.MAX_TIME_BETWEEN_HEARTBEATS
import com.parsely.parselyandroid.UpdateEngagementIntervalCalculator.Companion.OFFSET_MATCHING_BASE_INTERVAL
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

internal class UpdateEngagementIntervalCalculatorTest {

    private lateinit var sut: UpdateEngagementIntervalCalculator
    val fakeClock = FakeClock()

    @Before
    fun setUp() {
        sut = UpdateEngagementIntervalCalculator(fakeClock)
    }

    @Test
    fun `given the same time of start and current time, when calculating interval, return offset times backoff proportion`() {
        // given
        fakeClock.fakeNow = Duration.ZERO
        val startTime = Calendar.getInstance().apply {
            timeInMillis = 0
        }

        // when
        val result = sut.updateLatestInterval(startTime)

        // then
        // ((currentTime + offset) * BACKOFF_PROPORTION) * 1000
        // (0 + 35) * 0.3 * 1000 = 10500
        assertThat(result).isEqualTo(10500)
    }

    @Test
    fun `given a time that will cause the interval to surpass the MAX_TIME_BETWEEN_HEARTBEATS, when calculating interval, then return the MAX_TIME_BETWEEN_HEARTBEATS`() {
        // given
        // "excessiveTime" is a calculated point in time where the resulting interval would
        // naturally surpass MAX_TIME_BETWEEN_HEARTBEATS
        val excessiveTime =
            ((MAX_TIME_BETWEEN_HEARTBEATS / BACKOFF_PROPORTION) - OFFSET_MATCHING_BASE_INTERVAL) * 1000
        fakeClock.fakeNow = excessiveTime.milliseconds + 1.seconds
        val startTime = Calendar.getInstance().apply {
            timeInMillis = 0
        }

        // when
        val result = sut.updateLatestInterval(startTime)

        // then
        assertThat(result).isEqualTo(MAX_TIME_BETWEEN_HEARTBEATS * 1000)
    }

    @Test
    fun `given a specific time point, when updating latest interval, it correctly calculates the interval`() {
        // given
        val startTime = Calendar.getInstance().apply {
            timeInMillis = 0
        }
        fakeClock.fakeNow = 2.seconds

        // when
        val result = sut.updateLatestInterval(startTime)

        // then
        // ((currentTime + offset) * BACKOFF_PROPORTION) * 1000
        // (2 + 35) * 0.3 * 1000 = 11100
        assertThat(result).isEqualTo(11100)
    }

    class FakeClock : Clock() {
        var fakeNow = Duration.ZERO

        override val now: Duration
            get() = fakeNow
    }
}