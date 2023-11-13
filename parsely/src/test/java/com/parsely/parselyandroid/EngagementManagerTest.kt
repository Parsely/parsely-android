package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import java.util.Calendar
import java.util.TimeZone
import java.util.Timer
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.AbstractLongAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.assertj.core.api.Assertions.withinPercentage
import org.assertj.core.api.MapAssert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private typealias Event = MutableMap<String, Any>

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
internal class EngagementManagerTest {

    private lateinit var sut: EngagementManager
    private val tracker = FakeTracker()
    private val parentTimer = Timer()
    private val baseEvent: Event = mutableMapOf(
        "action" to "heartbeat",
        "data" to testData
    )

    @Test
    fun `when starting manager, then record the correct event after interval millis`() = runTest {
        // when
        sut = EngagementManager(
            tracker,
            parentTimer,
            DEFAULT_INTERVAL_MILLIS,
            baseEvent,
            FakeIntervalCalculator(),
            backgroundScope,
            FakeClock(testScheduler),
        )

        sut.start()
        advanceTimeBy(DEFAULT_INTERVAL_MILLIS)
        runCurrent()
        val timestamp = currentTime

        // then
        assertThat(tracker.events[0]).isCorrectEvent(
            // Ideally: totalTime should be equal to DEFAULT_INTERVAL_MILLIS
            withTotalTime = { isCloseTo(DEFAULT_INTERVAL_MILLIS, withinPercentage(10)) },
            // Ideally: timestamp should be equal to System.currentTimeMillis() at the time of recording the event
            withTimestamp = { isCloseTo(timestamp, within(20L)) }
        )
    }

    @Test
    fun `when starting manager, then schedule task each interval period`() = runTest {
        // when
        sut = EngagementManager(
            tracker,
            parentTimer,
            DEFAULT_INTERVAL_MILLIS,
            baseEvent,
            FakeIntervalCalculator(),
            backgroundScope,
            FakeClock(testScheduler),
        )
        sut.start()

        advanceTimeBy(DEFAULT_INTERVAL_MILLIS)
        val firstTimestamp = currentTime

        advanceTimeBy(DEFAULT_INTERVAL_MILLIS)
        val secondTimestamp = currentTime

        advanceTimeBy(DEFAULT_INTERVAL_MILLIS)
        val thirdTimestamp = currentTime

        runCurrent()

        val firstEvent = tracker.events[0]
        assertThat(firstEvent).isCorrectEvent(
            // Ideally: totalTime should be equal to DEFAULT_INTERVAL_MILLIS
            withTotalTime = { isCloseTo(DEFAULT_INTERVAL_MILLIS, withinPercentage(10)) },
            // Ideally: timestamp should be equal to `now` at the time of recording the event
            withTimestamp = { isCloseTo(firstTimestamp, within(20L)) }
        )
        val secondEvent = tracker.events[1]
        assertThat(secondEvent).isCorrectEvent(
            // Ideally: totalTime should be equal to DEFAULT_INTERVAL_MILLIS * 2
            withTotalTime = { isCloseTo(DEFAULT_INTERVAL_MILLIS * 2, withinPercentage(10)) },
            // Ideally: timestamp should be equal to `now` at the time of recording the event
            withTimestamp = { isCloseTo(secondTimestamp, within(20L)) }
        )
        val thirdEvent = tracker.events[2]
        assertThat(thirdEvent).isCorrectEvent(
            // Ideally: totalTime should be equal to DEFAULT_INTERVAL_MILLIS * 3
            withTotalTime = { isCloseTo(DEFAULT_INTERVAL_MILLIS * 3, withinPercentage(10)) },
            // Ideally: timestamp should be equal to `now` at the time of recording the event
            withTimestamp = { isCloseTo(thirdTimestamp, within(20L)) }
        )
    }

    @Test
    fun `given started manager, when stopping manager before interval ticks, then schedule an event`() = runTest {
        // given
        sut = EngagementManager(
            tracker,
            parentTimer,
            5.seconds.inWholeMilliseconds,
            baseEvent,
            object : FakeIntervalCalculator() {
                override fun calculate(startTime: Calendar): Long {
                    return 5.seconds.inWholeMilliseconds
                }
            },
            this,
            FakeClock(testScheduler)
        )
        sut.start()

        // when
        advanceTimeBy(12.seconds.inWholeMilliseconds)
        sut.stop()

        // then
        // first tick: after initial delay 5s, incremental addition 5s
        // second tick: after regular delay 5s, incremental addition 5s
        // third tick: after cancellation after 2s, incremental addition 2s
        assertThat(tracker.events).hasSize(3).satisfies({
            assertThat(it[0]).containsEntry("inc", 5L)
            assertThat(it[1]).containsEntry("inc", 5L)
            assertThat(it[2]).containsEntry("inc", 2L)
        })
    }

    private fun MapAssert<String, Any>.isCorrectEvent(
        withTotalTime: AbstractLongAssert<*>.() -> AbstractLongAssert<*>,
        withTimestamp: AbstractLongAssert<*>.() -> AbstractLongAssert<*>,
    ): MapAssert<String, Any> {
        return containsEntry("action", "heartbeat")
            // Incremental will be always 0 because the interval is lower than 1s
            .containsEntry("inc", 0L)
            .hasEntrySatisfying("tt") { totalTime ->
                totalTime as Long
                assertThat(totalTime).withTotalTime()
            }
            .hasEntrySatisfying("data") { data ->
                @Suppress("UNCHECKED_CAST")
                data as Map<String, Any>
                assertThat(data).hasEntrySatisfying("ts") { timestamp ->
                    timestamp as Long
                    assertThat(timestamp).withTimestamp()
                }.containsAllEntriesOf(testData.minus("ts"))
            }
    }

    private val now: Long
        get() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis

    class FakeTracker : ParselyTracker(
        "",
        0,
        ApplicationProvider.getApplicationContext()
    ) {
        val events = mutableListOf<Event>()

        override fun enqueueEvent(event: Event) {
            events += event
        }
    }

    open class FakeIntervalCalculator : HeartbeatIntervalCalculator(Clock()) {
        override fun calculate(startTime: Calendar): Long {
            return DEFAULT_INTERVAL_MILLIS
        }
    }

    class FakeClock(private val scheduler: TestCoroutineScheduler) : Clock() {
        override val now: Duration
            get() = scheduler.currentTime.milliseconds
    }

    private companion object {
        const val DEFAULT_INTERVAL_MILLIS = 100L

        // Additional time to wait to ensure that the timer has fired
        const val THREAD_SLEEPING_THRESHOLD = 50L
        val testData = mutableMapOf<String, Any>(
            "os" to "android",
            "parsely_site_uuid" to "e8857cbe-5ace-44f4-a85e-7e7475f675c5",
            "os_version" to "34",
            "manufacturer" to "Google",
            "ts" to 123L
        )
    }
}
