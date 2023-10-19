package com.parsely.parselyandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockWebServer
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

    @Before
    fun setUp() {
        sut = ParselyAPIConnection(FakeTracker)
    }

    @Test
    fun `when making connection without any events, then make GET request`() {
        // when
        sut.execute(mockServer.url("/").toString())

        // then
        assertThat(mockServer.takeRequest().method).isEqualTo("GET")
    }

    @Test
    fun `when making connection with events, then make POST request with JSON Content-Type header`() {
        // when
        sut.execute(
            mockServer.url("/").toString(), pixelPayload
        )

        // then
        assertThat(mockServer.takeRequest()).satisfies({
            assertThat(it.method).isEqualTo("POST")
            assertThat(it.headers["Content-Type"]).isEqualTo("application/json")
            assertThat(it.body.readUtf8()).isEqualTo(pixelPayload)
        })
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    companion object {
        val pixelPayload = """
{
    "events": [
        {
            "idsite": "example.com"
        },
        {
            "idsite": "example2.com"
        }
    ]
}            
""".trimIndent()
    }

    object FakeTracker : ParselyTracker(
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