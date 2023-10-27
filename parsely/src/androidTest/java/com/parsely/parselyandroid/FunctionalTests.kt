package com.parsely.parselyandroid

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.lang.reflect.Field
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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

    @Test
    fun appTracksEventsAboveQueueSizeLimit() {
        ActivityScenario.launch(SampleActivity::class.java).use { scenario ->
            scenario.onActivity { activity: Activity ->
                beforeEach(activity)
                server.enqueue(MockResponse().setResponseCode(200))
                parselyTracker = initializeTracker(activity)

                parselyTracker.trackPageview("url", null, null, null)

                server.takeRequest()
            }
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
