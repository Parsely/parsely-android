package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import java.util.Calendar
import java.util.Timer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class EngagementManagerTest {

    private lateinit var sut: EngagementManager
    private val tracker = FakeTracker()
    private val parentTimer = Timer()
    private val baseEvent = mapOf(
        "action" to "heartbeat",
        "data" to mapOf(
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
    fun `when starting manager, then record next event execution after interval millis`() {
        // when
        sut.start()
        Thread.sleep(DEFAULT_INTERVAL_MILLIS)

        // then
        assertThat(tracker.events).hasSize(1)
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
        val events = mutableListOf<MutableMap<String, Any>>()

        override fun enqueueEvent(event: MutableMap<String, Any>) {
            events += event
        }

    }

    class FakeIntervalCalculator : UpdateEngagementIntervalCalculator() {
        override fun updateLatestInterval(startTime: Calendar): Long {
            return DEFAULT_INTERVAL_MILLIS
        }
    }

    companion object {
        private const val DEFAULT_INTERVAL_MILLIS = 1 * 100L
    }
}