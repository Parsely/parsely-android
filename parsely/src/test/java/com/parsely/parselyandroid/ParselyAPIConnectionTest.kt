package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParselyAPIConnectionTest {

    private lateinit var sut: ParselyAPIConnection

    @Before
    fun setUp() {
        sut = ParselyAPIConnection(FakeTracker)
    }

    object FakeTracker : ParselyTracker(
        "siteId",
        10,
        ApplicationProvider.getApplicationContext()
    ) {

        var flushTimerStopped = false
        val events = mutableListOf<Map<String, Any>>()

        override fun purgeEventsQueue() {
            events.clear()
        }

        override fun stopFlushTimer() {
            flushTimerStopped = true
        }
    }
}