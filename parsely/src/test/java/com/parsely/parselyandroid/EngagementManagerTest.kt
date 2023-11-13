package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.AbstractLongAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.MapAssert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private typealias Event = MutableMap<String, Any>

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
internal class EngagementManagerTest {

    private lateinit var sut: EngagementManager
    private val tracker = FakeTracker()
    private val baseEvent: Event = mutableMapOf(
        "action" to "heartbeat",
        "data" to testData
    )

    @Test
    fun `when starting manager, then record the correct event after interval millis`() = runTest {
        // given
        sut = EngagementManager(
            tracker,
            DEFAULT_INTERVAL.inWholeMilliseconds,
            baseEvent,
            FakeIntervalCalculator(),
            backgroundScope,
            FakeClock(testScheduler),
        )

        // when
        sut.start()
        advanceTimeBy(DEFAULT_INTERVAL)
        runCurrent()

        // then
        assertThat(tracker.events[0]).isCorrectEvent(
            withIncrementalTime = { isEqualTo(DEFAULT_INTERVAL.inWholeSeconds)},
            withTotalTime = { isEqualTo(DEFAULT_INTERVAL.inWholeMilliseconds) },
            withTimestamp = { isEqualTo(currentTime) }
        )
    }

    @Test
    fun `when starting manager, then schedule task each interval period`() = runTest {
        // given
        sut = EngagementManager(
            tracker,
            DEFAULT_INTERVAL.inWholeMilliseconds,
            baseEvent,
            FakeIntervalCalculator(),
            backgroundScope,
            FakeClock(testScheduler),
        )
        sut.start()

        // when
        advanceTimeBy(DEFAULT_INTERVAL)
        val firstTimestamp = currentTime

        advanceTimeBy(DEFAULT_INTERVAL)
        val secondTimestamp = currentTime

        advanceTimeBy(DEFAULT_INTERVAL)
        runCurrent()
        val thirdTimestamp = currentTime

        // then
        val firstEvent = tracker.events[0]
        assertThat(firstEvent).isCorrectEvent(
            withIncrementalTime = { isEqualTo(DEFAULT_INTERVAL.inWholeSeconds) },
            withTotalTime = { isEqualTo(DEFAULT_INTERVAL.inWholeMilliseconds) },
            withTimestamp = { isEqualTo(firstTimestamp) }
        )
        val secondEvent = tracker.events[1]
        assertThat(secondEvent).isCorrectEvent(
            withIncrementalTime = { isEqualTo(DEFAULT_INTERVAL.inWholeSeconds) },
            withTotalTime = { isEqualTo((DEFAULT_INTERVAL * 2).inWholeMilliseconds) },
            withTimestamp = { isEqualTo(secondTimestamp) }
        )
        val thirdEvent = tracker.events[2]
        assertThat(thirdEvent).isCorrectEvent(
            withIncrementalTime = { isEqualTo(DEFAULT_INTERVAL.inWholeSeconds) },
            withTotalTime = { isEqualTo((DEFAULT_INTERVAL * 3).inWholeMilliseconds) },
            withTimestamp = { isEqualTo(thirdTimestamp) }
        )
    }

    @Test
    fun `given started manager, when stopping manager before interval ticks, then schedule an event`() = runTest {
        // given
        sut = EngagementManager(
            tracker,
            DEFAULT_INTERVAL.inWholeMilliseconds,
            baseEvent,
            FakeIntervalCalculator(),
            this,
            FakeClock(testScheduler)
        )
        sut.start()

        // when
        advanceTimeBy(70.seconds.inWholeMilliseconds)
        sut.stop()

        // then
        // first tick: after initial delay 30s, incremental addition 30s
        // second tick: after regular delay 30s, incremental addition 30s
        // third tick: after cancellation after 10s, incremental addition 10s
        assertThat(tracker.events).hasSize(3).satisfies({
            assertThat(it[0]).containsEntry("inc", 30L)
            assertThat(it[1]).containsEntry("inc", 30L)
            assertThat(it[2]).containsEntry("inc", 10L)
        })
    }

    private fun MapAssert<String, Any>.isCorrectEvent(
        withIncrementalTime: AbstractLongAssert<*>.() -> AbstractLongAssert<*>,
        withTotalTime: AbstractLongAssert<*>.() -> AbstractLongAssert<*>,
        withTimestamp: AbstractLongAssert<*>.() -> AbstractLongAssert<*>,
    ): MapAssert<String, Any> {
        return containsEntry("action", "heartbeat")
            .hasEntrySatisfying("inc") { incrementalTime ->
                incrementalTime as Long
                assertThat(incrementalTime).withIncrementalTime()
            }
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

    class FakeIntervalCalculator : HeartbeatIntervalCalculator(Clock()) {
        override fun calculate(startTime: Calendar): Long {
            return DEFAULT_INTERVAL.inWholeMilliseconds
        }
    }

    class FakeClock(private val scheduler: TestCoroutineScheduler) : Clock() {
        override val now: Duration
            get() = scheduler.currentTime.milliseconds
    }

    private companion object {
        val DEFAULT_INTERVAL = 30.seconds
        val testData = mutableMapOf<String, Any>(
            "os" to "android",
            "parsely_site_uuid" to "e8857cbe-5ace-44f4-a85e-7e7475f675c5",
            "os_version" to "34",
            "manufacturer" to "Google",
            "ts" to 123L
        )
    }
}
