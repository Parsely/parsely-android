package com.parsely.parselyandroid

import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ParselyAPIConnectionTest {

    private lateinit var sut: ParselyAPIConnection
    private val mockServer = MockWebServer()
    private val url = mockServer.url("").toString()

    @Before
    fun setUp() {
        sut = ParselyAPIConnection(url)
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
    fun `given unsuccessful response, when request is made, then return failure with exception`() =
        runTest {
            // given
            mockServer.enqueue(MockResponse().setResponseCode(400))
            val sampleEvents = mapOf("idsite" to "example.com")

            // when
            val result = sut.send(pixelPayload)
            runCurrent()

            // then
            assertThat(result.isFailure).isTrue
            assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        }

    companion object {
        val pixelPayload: String =
            this::class.java.getResource("pixel_payload.json")?.readText().orEmpty()
    }
}
