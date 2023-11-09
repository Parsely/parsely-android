package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.IOException
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParselyAPIConnectionTest {

    private lateinit var sut: ParselyAPIConnection
    private val mockServer = MockWebServer()
    private val url = mockServer.url("").toString()
    private val tracker = FakeTracker()

    @Before
    fun setUp() {
        sut = ParselyAPIConnection(url, tracker)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `given successful response, when making connection with events, then make POST request with JSON Content-Type header`() =
        runTest {
            // given
            mockServer.enqueue(MockResponse().setResponseCode(200))

            // when
            val result = sut.send(pixelPayload)
            runCurrent()

            // then
            assertThat(mockServer.takeRequest()).satisfies({
                assertThat(it.method).isEqualTo("POST")
                assertThat(it.headers["Content-Type"]).isEqualTo("application/json")
                assertThat(it.body.readUtf8()).isEqualTo(pixelPayload)
            })
            assertThat(result.isSuccess).isTrue
        }

    @Test
    fun `given successful response, when request is made, then purge events queue and stop flush timer`() =
        runTest {
            // given
            mockServer.enqueue(MockResponse().setResponseCode(200))
            tracker.events.add(mapOf("idsite" to "example.com"))

            // when
            val result = sut.send(pixelPayload)
            runCurrent()

            // then
            assertThat(tracker.events).isEmpty()
            assertThat(tracker.flushTimerStopped).isTrue
            assertThat(result.isSuccess).isTrue
        }

    @Test
    fun `given unsuccessful response, when request is made, then do not purge events queue and do not stop flush timer`() =
        runTest {
            // given
            mockServer.enqueue(MockResponse().setResponseCode(400))
            val sampleEvents = mapOf("idsite" to "example.com")
            tracker.events.add(sampleEvents)

            // when
            val result = sut.send(pixelPayload)
            runCurrent()

            // then
            assertThat(tracker.events).containsExactly(sampleEvents)
            assertThat(tracker.flushTimerStopped).isFalse
            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
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
