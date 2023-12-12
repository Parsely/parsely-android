package com.parsely.parselyandroid

import com.parsely.parselyandroid.HeartbeatIntervalCalculator.Companion.MAX_TIME_BETWEEN_HEARTBEATS
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

internal class HeartbeatIntervalCalculatorTest {

    private lateinit var sut: HeartbeatIntervalCalculator
    private val fakeClock = FakeClock()

    @Before
    fun setUp() {
        sut = HeartbeatIntervalCalculator(fakeClock)
    }

    @Test
    fun `given the same time of start and current time, when calculating interval, return offset times backoff proportion`() {
        // given
        fakeClock.fakeNow = Duration.ZERO
        val startTime = Duration.ZERO

        // when
        val result = sut.calculate(startTime)

        // then
        // ((currentTime + offset) * backoff) and then in milliseconds
        // (0 + 35) * 0.3 * 1000 = 10500
        assertThat(result).isEqualTo(10500)
    }

    @Test
    fun `given a time that will cause the interval to surpass the MAX_TIME_BETWEEN_HEARTBEATS, when calculating interval, then return the MAX_TIME_BETWEEN_HEARTBEATS`() {
        // given
        // "excessiveTime" is a calculated point in time where the resulting interval would
        // surpass MAX_TIME_BETWEEN_HEARTBEATS
        // (currentTime + offset) * backoff = max
        // currentTime = (max / backoff) - offset, so
        // (15 minutes / 0.3) - 35 seconds = 2965 seconds. Add 1 second to be over the limit
        val excessiveTime = 2965.seconds + 1.seconds
        fakeClock.fakeNow = excessiveTime
        val startTime = Duration.ZERO

        // when
        val result = sut.calculate(startTime)

        // then
        assertThat(result).isEqualTo(MAX_TIME_BETWEEN_HEARTBEATS.inWholeMilliseconds)
    }

    @Test
    fun `given a specific time point, when updating latest interval, it correctly calculates the interval`() {
        // given
        val startTime = Duration.ZERO
        fakeClock.fakeNow = 2.seconds

        // when
        val result = sut.calculate(startTime)

        // then
        // ((currentTime + offset) * backoff) and then in milliseconds
        // (2 + 35) * 0.3 * 1000 = 11100
        assertThat(result).isEqualTo(11100)
    }

    class FakeClock : Clock() {
        var fakeNow = Duration.ZERO

        override val now: Duration
            get() = fakeNow
    }
}
