package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import java.util.Calendar
import java.util.Timer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.withinPercentage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private typealias Event = MutableMap<String, Any>

@RunWith(RobolectricTestRunner::class)
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

        // then

        val trackedEvent = tracker.events[0]

        assertThat(trackedEvent)
            .containsEntry("action", "heartbeat")
            .hasEntrySatisfying("inc") { incremental ->
                incremental as Long
                // Ideally: incremental should be 0
                assertThat(incremental).isLessThan(5)
            }
            .hasEntrySatisfying("tt") { totalTime ->
                totalTime as Long
                // Ideally: totalTime should be equal to DEFAULT_INTERVAL_MILLIS
                assertThat(totalTime).isCloseTo(DEFAULT_INTERVAL_MILLIS, withinPercentage(10))
            }
            .hasEntrySatisfying("data") { data ->
                data as Map<String, Any>
                assertThat(data).hasEntrySatisfying("ts") { timestamp ->
                    timestamp as Long
                    assertThat(timestamp).isCloseTo(
                        System.currentTimeMillis() + DEFAULT_INTERVAL_MILLIS,
                        withinPercentage(5)
                    )
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

    private fun sleep(millis: Long) = Thread.sleep(millis + THREAD_SLEEPING_THRESHOLD)

    companion object {
        private const val DEFAULT_INTERVAL_MILLIS = 1 * 100L

        // Additional time to wait to ensure that the timer has fired
        private const val THREAD_SLEEPING_THRESHOLD = 100L
    }
}