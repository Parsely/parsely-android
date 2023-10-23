package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper.shadowMainLooper

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ParselyAPIConnectionTest {

    private lateinit var sut: ParselyAPIConnection
    private val mockServer = MockWebServer()
    private val url = mockServer.url("").toString()
    private val tracker = FakeTracker()

    @Before
    fun setUp() {
        sut = ParselyAPIConnection(tracker)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `given successful response, when making connection without any events, then make GET request`() {
        // given
        mockServer.enqueue(MockResponse().setResponseCode(200))

        // when
        sut.execute(url).get()
        shadowMainLooper().idle();

        // then
        val request = mockServer.takeRequest()
        assertThat(request).satisfies({
            assertThat(it.method).isEqualTo("GET")
            assertThat(it.failure).isNull()
        })
    }

    @Test
    fun `given successful response, when making connection with events, then make POST request with JSON Content-Type header`() {
        // given
        mockServer.enqueue(MockResponse().setResponseCode(200))

        // when
        sut.execute(url, pixelPayload).get()
        shadowMainLooper().idle();

        // then
        assertThat(mockServer.takeRequest()).satisfies({
            assertThat(it.method).isEqualTo("POST")
            assertThat(it.headers["Content-Type"]).isEqualTo("application/json")
            assertThat(it.body.readUtf8()).isEqualTo(pixelPayload)
        })
    }

    @Test
    fun `given successful response, when request is made, then purge events queue and stop flush timer`() {
        // given
        mockServer.enqueue(MockResponse().setResponseCode(200))
        tracker.events.add(mapOf("idsite" to "example.com"))

        // when
        sut.execute(url).get()
        shadowMainLooper().idle();

        // then
        assertThat(tracker.events).isEmpty()
        assertThat(tracker.flushTimerStopped).isTrue
    }

    @Test
    fun `given unsuccessful response, when request is made, then do not purge events queue and do not stop flush timer`() {
        // given
        mockServer.enqueue(MockResponse().setResponseCode(400))
        val sampleEvents = mapOf("idsite" to "example.com")
        tracker.events.add(sampleEvents)

        // when
        sut.execute(url).get()
        shadowMainLooper().idle();

        // then
        assertThat(tracker.events).containsExactly(sampleEvents)
        assertThat(tracker.flushTimerStopped).isFalse
    }

    companion object {
        val pixelPayload: String =
            this::class.java.getResource("pixel_payload.json")?.readText().orEmpty()
    }

    private class FakeTracker : ParselyTracker(
        "siteId", 10, ApplicationProvider.getApplicationContext()
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
