package com.parsely.parselyandroid

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.lang.reflect.Field
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchEvent
import java.nio.file.WatchService
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

                // Waits for the SDK to save events to disk
                val createLocalStorageEvents = appsFiles.waitForFileEvents(2)
                assertThat(createLocalStorageEvents).satisfiesExactly(
                    // Checks for local storage file creation
                    { event -> assertThat(event.kind()).isEqualTo(ENTRY_CREATE) },
                    // Checks if local storage file was modified
                    { event -> assertThat(event.kind()).isEqualTo(ENTRY_MODIFY) },
                )
                assertThat(locallyStoredEvents).hasSize(51)
            }

            val dropLocalStorageEvent = appsFiles.waitForFileEvents(1)

            // Waits for the SDK to send events (flush interval passes)
            server.takeRequest()

            assertThat(dropLocalStorageEvent).satisfiesExactly(
                { event -> assertThat(event.kind()).isEqualTo(ENTRY_MODIFY) },
            )
            assertThat(locallyStoredEvents).hasSize(0)
        }
    }

    private val locallyStoredEvents
        get() = FileInputStream(File("$appsFiles/parsely-events.ser")).use {
            ObjectInputStream(it).use { objectInputStream ->
                @Suppress("UNCHECKED_CAST")
                objectInputStream.readObject() as ArrayList<Map<String, Any>>
            }
        }


    private fun Path.waitForFileEvents(numberOfEvents: Int): List<WatchEvent<*>> {
        val service = watch()
        val events = LinkedHashSet<WatchEvent<*>>()
        while (true) {
            val key = service.poll()
            val polledEvents =
                key?.pollEvents()?.filter { it.context().toString() == "parsely-events.ser" }
                    .orEmpty()
            events.addAll(polledEvents)
            println("[Parsely] Caught ${events.size} file events")
            if (events.size == numberOfEvents) {
                key?.reset()
                break
            }
            Thread.sleep(500)
        }
        return events.toList()
    }

    private fun Path.watch(): WatchService {
        val watchService = this.fileSystem.newWatchService()
        register(watchService, ENTRY_CREATE, ENTRY_MODIFY)

        return watchService
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
