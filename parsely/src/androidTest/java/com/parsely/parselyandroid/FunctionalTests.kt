package com.parsely.parselyandroid

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.lang.reflect.Field
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
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

        if (File("$appsFiles/parsely-events.ser").exists()) {
            throw RuntimeException("Local storage file exists. Something went wrong with orchestrating the tests.")
        }
    }

    /**
     * In this scenario, the consumer application tracks more than 50 events-threshold during a flush interval.
     * The SDK will save the events to disk and send them in the next flush interval.
     * At the end, when all events are sent, the SDK will delete the content of local storage file.
     */
    @Test
    fun appTracksEventsAboveQueueSizeLimit() {
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
            server.takeRequest()

            runBlocking {
                withTimeoutOrNull(500.milliseconds) {
                    while (true) {
                        yield()
                        if (locallyStoredEvents.size == 0) {
                            break
                        }
                    }
                } ?: fail("Local storage file is not empty!")
            }

        }
    }

    private val locallyStoredEvents
        get() = FileInputStream(File("$appsFiles/parsely-events.ser")).use {
            ObjectInputStream(it).use { objectInputStream ->
                @Suppress("UNCHECKED_CAST")
                objectInputStream.readObject() as ArrayList<Map<String, Any>>
            }
        }

    private fun initializeTracker(activity: Activity): ParselyTracker {
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
        val flushInterval = 10.seconds
    }

    class SampleActivity : Activity()
}
