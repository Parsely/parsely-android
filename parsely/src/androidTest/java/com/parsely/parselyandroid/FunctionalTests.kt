package com.parsely.parselyandroid

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.lang.reflect.Field
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class FunctionalTests {

    private lateinit var parselyTracker: ParselyTracker
    private val server = MockWebServer()
    private val url = server.url("/").toString()
    private lateinit var appsFiles: Path

    private fun beforeEach(activity: Activity) {
        appsFiles = Path(activity.filesDir.path)

        if (File("$appsFiles/$localStorageFileName").exists()) {
            throw RuntimeException("Local storage file exists. Something went wrong with orchestrating the tests.")
        }
    }

    /**
     * In this scenario, the consumer application tracks 51 events-threshold during a flush interval.
     * The SDK will save the events to disk and send them in the next flush interval.
     * At the end, when all events are sent, the SDK will delete the content of local storage file.
     */
    @Test
    fun appTracksEventsDuringTheFlushInterval() {
        ActivityScenario.launch(SampleActivity::class.java).use { scenario ->
            scenario.onActivity { activity: Activity ->
                beforeEach(activity)
                server.enqueue(MockResponse().setResponseCode(200))
                parselyTracker = initializeTracker(activity)

                repeat(51) {
                    parselyTracker.trackPageview("url", null, null, null)
                }
            }

            // Waits for the SDK to send events (flush interval passes)
            val requestPayload = server.takeRequest().toMap()
            assertThat(requestPayload["events"]).hasSize(51)

            // Wait a moment to give SDK time to delete the content of local storage file
            waitFor { locallyStoredEvents.isEmpty() }
            assertThat(locallyStoredEvents).isEmpty()
        }
    }

    /**
     * In this scenario, the consumer app tracks 2 events during the first flush interval.
     * Then, we validate, that after flush interval passed the SDK sends the events
     * to Parse.ly servers.
     *
     * Then, the consumer app tracks another event and we validate that the SDK sends the event
     * to Parse.ly servers as well.
     */
    @Test
    fun appFlushesEventsAfterFlushInterval() {
        ActivityScenario.launch(SampleActivity::class.java).use { scenario ->
            scenario.onActivity { activity: Activity ->
                beforeEach(activity)
                server.enqueue(MockResponse().setResponseCode(200))
                parselyTracker = initializeTracker(activity)

                parselyTracker.trackPageview("url", null, null, null)
            }

            Thread.sleep((defaultFlushInterval / 2).inWholeMilliseconds)

            scenario.onActivity {
                parselyTracker.trackPageview("url", null, null, null)
            }

            Thread.sleep((defaultFlushInterval / 2).inWholeMilliseconds)

            val firstRequestPayload = server.takeRequest(2000, TimeUnit.MILLISECONDS)?.toMap()
            assertThat(firstRequestPayload!!["events"]).hasSize(2)

            scenario.onActivity {
                parselyTracker.trackPageview("url", null, null, null)
            }

            Thread.sleep(defaultFlushInterval.inWholeMilliseconds)

            val secondRequestPayload = server.takeRequest(2000, TimeUnit.MILLISECONDS)?.toMap()
            assertThat(secondRequestPayload!!["events"]).hasSize(1)
        }
    }

    /**
     * In this scenario we "stress test" the concurrency model to see if we have any conflict during
     *
     * - Unexpectedly high number of recorded events in small intervals (I/O locking)
     * - Scenario in which a request is sent at the same time as new events are recorded
     */
    @Test
    fun stressTest() {
        val eventsToSend = 500

        ActivityScenario.launch(SampleActivity::class.java).use { scenario ->
            scenario.onActivity { activity: Activity ->
                beforeEach(activity)
                server.enqueue(MockResponse().setResponseCode(200))
                parselyTracker = initializeTracker(activity)

                repeat(eventsToSend) {
                    parselyTracker.trackPageview("url", null, null, null)
                }
            }

            // Wait some time to give events chance to be saved in local data storage
            Thread.sleep((defaultFlushInterval * 2).inWholeMilliseconds)

            // Catch up to 10 requests. We don't know how many requests the device we test on will
            // perform. It's probably more like 1-2, but we're on safe (not flaky) side here.
            val requests = (1..10).mapNotNull {
                runCatching { server.takeRequest(100, TimeUnit.MILLISECONDS) }.getOrNull()
            }.flatMap {
                it.toMap()["events"]!!
            }

            assertThat(requests).hasSize(eventsToSend)
        }
    }

    private fun RecordedRequest.toMap(): Map<String, List<Event>> {
        val listType: TypeReference<Map<String, List<Event>>> =
            object : TypeReference<Map<String, List<Event>>>() {}

        return ObjectMapper().readValue(body.readUtf8(), listType)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Event(
        @JsonProperty("idsite") var idsite: String,
    )

    private val locallyStoredEvents
        get() = FileInputStream(File("$appsFiles/$localStorageFileName")).use {
            ObjectInputStream(it).use { objectInputStream ->
                @Suppress("UNCHECKED_CAST")
                objectInputStream.readObject() as ArrayList<Map<String, Any>>
            }
        }

    private fun initializeTracker(
        activity: Activity,
        flushInterval: Duration = defaultFlushInterval
    ): ParselyTracker {
        return ParselyTracker.sharedInstance(
            siteId, flushInterval.inWholeSeconds.toInt(), activity.application
        ).apply {
            val f: Field = this::class.java.getDeclaredField("ROOT_URL")
            f.isAccessible = true
            f.set(this, url)
        }
    }

    private companion object {
        const val siteId = "123"
        const val localStorageFileName = "parsely-events.ser"
        val defaultFlushInterval = 5.seconds
    }

    class SampleActivity : Activity()

    private fun waitFor(condition: () -> Boolean) = runBlocking {
        withTimeoutOrNull(500.milliseconds) {
            while (true) {
                yield()
                if (condition()) {
                    break
                }
            }
        }
    }
}
