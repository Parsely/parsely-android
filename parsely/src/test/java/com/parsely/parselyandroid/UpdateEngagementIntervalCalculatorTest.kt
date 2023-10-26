package com.parsely.parselyandroid

import com.parsely.parselyandroid.UpdateEngagementIntervalCalculator.BACKOFF_PROPORTION
import com.parsely.parselyandroid.UpdateEngagementIntervalCalculator.OFFSET_MATCHING_BASE_INTERVAL
import java.util.Calendar
import java.util.TimeZone
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
        fakeClock.fakeNow = 0L
        val startTime = Calendar.getInstance().apply {
            timeInMillis = 0
        }

        // when
        val result = sut.updateLatestInterval(startTime)

        // then
        // ((currentTimeInMillis + OFFSET_MATCHING_BASE_INTERVAL) * BACKOFF_PROPORTION) * 1000
        // (0 + 35) * 0.3 * 1000 = 10500 but the result is 10000 because newInterval
        // is casted from double to long - instead of 10.5 seconds, it's 10 seconds
        assertThat(result).isEqualTo(10000)
    }

    class FakeClock : Clock() {
        var fakeNow = 0L

        override val now: Long
            get() = fakeNow
    }
}