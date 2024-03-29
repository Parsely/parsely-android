package com.parsely.parselyandroid

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlushManagerTest {

    @Test
    fun `when timer starts and interval time passes, then flush queue`() = runTest {
        var flushEventsCounter = 0
        val sut =
            ParselyFlushManager({ flushEventsCounter++ }, DEFAULT_INTERVAL_MILLIS, backgroundScope)

        sut.start()
        advanceTimeBy(DEFAULT_INTERVAL_MILLIS)
        runCurrent()

        assertThat(flushEventsCounter).isEqualTo(1)
    }

    @Test
    fun `when timer starts and three interval time passes, then flush queue 3 times`() = runTest {
        var flushEventsCounter = 0
        val sut =
            ParselyFlushManager({ flushEventsCounter++ }, DEFAULT_INTERVAL_MILLIS, backgroundScope)

        sut.start()
        advanceTimeBy(3 * DEFAULT_INTERVAL_MILLIS)
        runCurrent()

        assertThat(flushEventsCounter).isEqualTo(3)
    }

    @Test
    fun `when timer starts and is stopped after 2 intervals passes, then flush queue 2 times`() =
        runTest {
            var flushEventsCounter = 0
            val sut =
                ParselyFlushManager({ flushEventsCounter++ }, DEFAULT_INTERVAL_MILLIS, backgroundScope)

            sut.start()
            advanceTimeBy(2 * DEFAULT_INTERVAL_MILLIS)
            runCurrent()
            sut.stop()
            advanceTimeBy(DEFAULT_INTERVAL_MILLIS)
            runCurrent()

            assertThat(flushEventsCounter).isEqualTo(2)
        }

    @Test
    fun `when timer starts, is stopped before end of interval and then time of interval passes, then do not flush queue`() =
        runTest {
            var flushEventsCounter = 0
            val sut =
                ParselyFlushManager({ flushEventsCounter++ }, DEFAULT_INTERVAL_MILLIS, backgroundScope)

            sut.start()
            advanceTimeBy(DEFAULT_INTERVAL_MILLIS / 2)
            runCurrent()
            sut.stop()
            advanceTimeBy(DEFAULT_INTERVAL_MILLIS / 2)
            runCurrent()

            assertThat(flushEventsCounter).isEqualTo(0)
        }

    @Test
    fun `when timer starts, and another timer starts after some time, then flush queue according to the first start`() =
        runTest {
            var flushEventsCounter = 0
            val sut =
                ParselyFlushManager({ flushEventsCounter++ }, DEFAULT_INTERVAL_MILLIS, backgroundScope)

            sut.start()
            advanceTimeBy(DEFAULT_INTERVAL_MILLIS / 2)
            runCurrent()
            sut.start()
            advanceTimeBy(DEFAULT_INTERVAL_MILLIS / 2)
            runCurrent()

            assertThat(flushEventsCounter).isEqualTo(1)

            advanceTimeBy(DEFAULT_INTERVAL_MILLIS / 2)
            runCurrent()

            assertThat(flushEventsCounter).isEqualTo(1)
        }

    private companion object {
        val DEFAULT_INTERVAL_MILLIS: Long = 30.seconds.inWholeMilliseconds
    }
}
