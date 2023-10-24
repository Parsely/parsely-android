package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import java.util.Calendar
import java.util.TimeZone
import java.util.Timer
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

@RunWith(RobolectricTestRunner::class)
@Suppress("UNCHECKED_CAST")
internal class EngagementManagerTest {

    private lateinit var sut: EngagementManager
    private val tracker = FakeTracker()
    private val parentTimer = Timer()
    private val baseEvent: Event = mutableMapOf(
        "action" to "heartbeat",
        "data" to mutableMapOf<String, Any>(
            "os" to "android",
            "parsely_site_uuid" to "e8857cbe-5ace-44f4-a85e-7e7475f675c5",
            "os_version" to "34",
            "manufacturer" to "Google",
            "ts" to 1697638552181
        )
    )

    @Before
    fun setUp() {
        sut = EngagementManager(
            tracker,
            parentTimer,
            DEFAULT_INTERVAL_MILLIS,
            baseEvent,
            FakeIntervalCalculator()
        )
    }

    @Test
    fun `when starting manager, then record the correct event after interval millis`() {
        // when
        sut.start()
        sleep(DEFAULT_INTERVAL_MILLIS)
        val timestamp = now - THREAD_SLEEPING_THRESHOLD

        // then
        assertThat(tracker.events[0]).isCorrectEvent(
            withTotalTime = {
                // Ideally: totalTime should be equal to DEFAULT_INTERVAL_MILLIS
                isCloseTo(DEFAULT_INTERVAL_MILLIS, withinPercentage(10))
            },
            withTimestamp = {
                // Ideally: timestamp should be equal to System.currentTimeMillis() at the time of recording the event
                isCloseTo(
                    timestamp,
                    within(100L)
                )
            }
        )
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
                data as Map<String, Any>
                assertThat(data).hasEntrySatisfying("ts") { timestamp ->
                    timestamp as Long
                    assertThat(timestamp).withTimestamp()
                }.containsEntry("os", "android")
                    .containsEntry("parsely_site_uuid", "e8857cbe-5ace-44f4-a85e-7e7475f675c5")
                    .containsEntry("os_version", "34")
                    .containsEntry("manufacturer", "Google")
            }
    }

    @Test
    fun `when starting manager, then schedule task each interval period`() {
        sut.start()

        Thread.sleep(DEFAULT_INTERVAL_MILLIS)
        assertThat(tracker.events).hasSize(1)

        Thread.sleep(DEFAULT_INTERVAL_MILLIS)
        assertThat(tracker.events).hasSize(2)

        Thread.sleep(DEFAULT_INTERVAL_MILLIS)
        assertThat(tracker.events).hasSize(3)
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

    class FakeIntervalCalculator : UpdateEngagementIntervalCalculator() {
        override fun updateLatestInterval(startTime: Calendar): Long {
            return DEFAULT_INTERVAL_MILLIS
        }
    }

    private val now: Long
        get() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis

    private fun sleep(millis: Long) = Thread.sleep(millis + THREAD_SLEEPING_THRESHOLD)

    companion object {
        private const val DEFAULT_INTERVAL_MILLIS = 100L

        // Additional time to wait to ensure that the timer has fired
        private const val THREAD_SLEEPING_THRESHOLD = 50L
    }
}