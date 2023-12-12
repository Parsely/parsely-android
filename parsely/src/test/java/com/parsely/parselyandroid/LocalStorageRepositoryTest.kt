package com.parsely.parselyandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LocalStorageRepositoryTest {

    private lateinit var sut: LocalStorageRepository
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        sut = LocalStorageRepository(context)
    }

    @Test
    fun `given the list of events, when persisting the list, then querying the list returns the same result`() = runTest {
        // given
        val eventsList = (1..10).map { mapOf("index" to it) }

        // when
        sut.insertEvents(eventsList)
        runCurrent()

        // then
        assertThat(sut.getStoredQueue()).hasSize(10).containsExactlyInAnyOrderElementsOf(eventsList)
    }

    @Test
    fun `given no locally stored list, when requesting stored queue, then return an empty list`() = runTest {
        assertThat(sut.getStoredQueue()).isEmpty()
    }

    @Test
    fun `given stored queue with some elements, when persisting an event, then assert there'll be no duplicates`() = runTest {
        // given
        val storedQueue = (1..5).map { mapOf("index" to it) }
        val newEvents = (3..10).map { mapOf("index" to it) }
        sut.insertEvents(storedQueue)
        runCurrent()

        // when
        sut.insertEvents(newEvents)
        runCurrent()

        // then
        val expectedQueue = (1..10).map { mapOf("index" to it) }
        assertThat(sut.getStoredQueue()).hasSize(10).containsExactlyInAnyOrderElementsOf(expectedQueue)
    }

    @Test
    fun `given stored queue, when removing some events, then assert queue is doesn't contain removed events and contains not removed events`() = runTest {
        // given
        val initialList = (1..10).map { mapOf("index" to it) }
        sut.insertEvents(initialList)
        runCurrent()
        val eventsToRemove = initialList.slice(0..5)
        val eventsToKeep = initialList.slice(6..9)

        // when
        sut.remove(eventsToRemove)

        // then
        assertThat(sut.getStoredQueue())
            .hasSize(4)
            .containsExactlyInAnyOrderElementsOf(eventsToKeep)
            .doesNotContainAnyElementsOf(eventsToRemove)
    }

    @Test
    fun `given stored file with serialized events, when querying the queue, then list has expected events`() = runTest {
        // given
        val file = File(context.filesDir.path + "/parsely-events.ser")
        File(ClassLoader.getSystemResource("valid-java-parsely-events.ser")?.path!!).copyTo(file)

        // when
        val queue = sut.getStoredQueue()

        // then
        assertThat(queue).isEqualTo(
            listOf(
                mapOf(
                    "idsite" to "example.com",
                    "urlref" to "http://example.com/",
                    "data" to mapOf<String, Any>(
                        "manufacturer" to "Google",
                        "os" to "android",
                        "os_version" to "33",
                        "parsely_site_uuid" to "b325e2c9-498c-4331-a967-2d6049317c77",
                        "ts" to 1698918720863L
                    ),
                    "pvid" to "272cc2b8-5acc-4a70-80c7-20bb6eb843e4",
                    "action" to "pageview",
                    "url" to "http://example.com/article1.html"
                ),
                mapOf(
                    "idsite" to "example.com",
                    "urlref" to "http://example.com/",
                    "data" to mapOf<String, Any>(
                        "manufacturer" to "Google",
                        "os" to "android",
                        "os_version" to "33",
                        "parsely_site_uuid" to "b325e2c9-498c-4331-a967-2d6049317c77",
                        "ts" to 1698918742375L
                    ),
                    "pvid" to "e94567ec-3459-498c-bf2e-6a1b85ed5a82",
                    "action" to "pageview",
                    "url" to "http://example.com/article1.html"
                )
            )
        )
    }
}
