package com.parsely.parselyandroid

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
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
                initializeTracker(activity)

                repeat(51) {
                    parselyTracker.trackPageview("url")
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
                initializeTracker(activity)

                parselyTracker.trackPageview("url")
            }

            Thread.sleep((defaultFlushInterval / 2).inWholeMilliseconds)

            scenario.onActivity {
                parselyTracker.trackPageview("url")
            }

            Thread.sleep((defaultFlushInterval / 2).inWholeMilliseconds)

            val firstRequestPayload = server.takeRequest(2000, TimeUnit.MILLISECONDS)?.toMap()
            assertThat(firstRequestPayload!!["events"]).hasSize(2)

            scenario.onActivity {
                parselyTracker.trackPageview("url")
            }

            Thread.sleep(defaultFlushInterval.inWholeMilliseconds)

            val secondRequestPayload = server.takeRequest(2000, TimeUnit.MILLISECONDS)?.toMap()
            assertThat(secondRequestPayload!!["events"]).hasSize(1)
        }
    }

    /**
     * In this scenario, the consumer application:
     * 1. Goes to the background
     * 2. Is re-launched
     * This pattern occurs twice, which allows us to confirm the following assertions:
     * 1. The event request is triggered when the consumer application is moved to the background
     * 2. If the consumer application is sent to the background again within a short interval,
     * the request is not duplicated.
     */
    @Test
    fun appSendsEventsWhenMovedToBackgroundAndDoesntSendDuplicatedRequestWhenItsMovedToBackgroundAgainQuickly() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(SampleActivity::class.java).use { scenario ->
            scenario.onActivity { activity: Activity ->
                beforeEach(activity)
                server.enqueue(MockResponse().setResponseCode(200))
                server.enqueue(MockResponse().setResponseCode(200))
                initializeTracker(activity, flushInterval = 1.hours)

                repeat(20) {
                    parselyTracker.trackPageview("url")
                }
            }

            device.pressHome()
            device.pressRecentApps()
            device.findObject(UiSelector().descriptionContains("com.parsely")).click()
            device.pressHome()

            val firstRequest = server.takeRequest(10000, TimeUnit.MILLISECONDS)?.toMap()
            val secondRequest = server.takeRequest(10000, TimeUnit.MILLISECONDS)?.toMap()

            assertThat(firstRequest!!["events"]).hasSize(20)
            assertThat(secondRequest).isNull()
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
                initializeTracker(activity)

                repeat(eventsToSend) {
                    parselyTracker.trackPageview("url")
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

    /**
     * In this scenario consumer app starts an engagement session and after 27150 ms,
     * it stops the session.
     *
     * Intervals:
     * With current implementation of `HeartbeatIntervalCalculator`, the next intervals are:
     * - 10500ms for the first interval
     * - 13650ms for the second interval
     *
     * So after ~27,2s we should observe
     *  - 2 `heartbeat` events from `startEngagement` + 1 `heartbeat` event caused by `stopEngagement` which is triggered during engagement interval
     *
     * Time off-differences in assertions are acceptable, because it's a time-sensitive test
     */
    @Test
    fun engagementManagerTest() {
        val engagementUrl = "engagementUrl"
        var startTimestamp = Duration.ZERO
        val firstInterval = 10500.milliseconds
        val secondInterval = 13650.milliseconds
        val pauseInterval = 3.seconds
        ActivityScenario.launch(SampleActivity::class.java).use { scenario ->
            // given
            scenario.onActivity { activity: Activity ->
                beforeEach(activity)
                server.enqueue(MockResponse().setResponseCode(200))
                initializeTracker(activity, flushInterval = 30.seconds)

                // when
                startTimestamp = System.currentTimeMillis().milliseconds
                parselyTracker.trackPageview("url")
                parselyTracker.startEngagement(engagementUrl)
            }

            Thread.sleep((firstInterval + secondInterval + pauseInterval).inWholeMilliseconds)
            parselyTracker.stopEngagement()

            // then
            val request = server.takeRequest(35, TimeUnit.SECONDS)!!.toMap()["events"]!!

            assertThat(
                request.sortedBy { it.data.timestamp }
                    .filter { it.action == "heartbeat" }
            ).hasSize(3)
                .satisfies({
                    val firstEvent = it[0]
                    val secondEvent = it[1]
                    val thirdEvent = it[2]

                    assertThat(firstEvent.data.timestamp).isCloseTo(
                        (startTimestamp + firstInterval).inWholeMilliseconds,
                        within(1.seconds.inWholeMilliseconds)
                    )
                    assertThat(firstEvent.totalTime).isCloseTo(
                        firstInterval.inWholeMilliseconds,
                        within(100L)
                    )
                    assertThat(firstEvent.incremental).isCloseTo(
                        firstInterval.inWholeSeconds,
                        within(1L)
                    )

                    assertThat(secondEvent.data.timestamp).isCloseTo(
                        (startTimestamp + firstInterval + secondInterval).inWholeMilliseconds,
                        within(1.seconds.inWholeMilliseconds)
                    )
                    assertThat(secondEvent.totalTime).isCloseTo(
                        (firstInterval + secondInterval).inWholeMilliseconds,
                        within(100L)
                    )
                    assertThat(secondEvent.incremental).isCloseTo(
                        secondInterval.inWholeSeconds,
                        within(1L)
                    )

                    assertThat(thirdEvent.data.timestamp).isCloseTo(
                        (startTimestamp + firstInterval + secondInterval + pauseInterval).inWholeMilliseconds,
                        within(1.seconds.inWholeMilliseconds)
                    )
                    assertThat(thirdEvent.totalTime).isCloseTo(
                        (firstInterval + secondInterval + pauseInterval).inWholeMilliseconds,
                        within(100L)
                    )
                    assertThat(thirdEvent.incremental).isCloseTo(
                        (pauseInterval).inWholeSeconds,
                        within(1L)
                    )
                })
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
        @JsonProperty("action") var action: String,
        @JsonProperty("data") var data: ExtraData,
        @JsonProperty("tt") var totalTime: Long,
        @JsonProperty("inc") var incremental: Long,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ExtraData(
        @JsonProperty("ts") var timestamp: Long,
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
    )  {
        val field: Field = ParselyTrackerInternal::class.java.getDeclaredField("ROOT_URL")
        field.isAccessible = true
        field.set(this, url)
        ParselyTracker.init(
            siteId, flushInterval.inWholeSeconds.toInt(), activity.application
        )
        parselyTracker = ParselyTracker.sharedInstance()
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
