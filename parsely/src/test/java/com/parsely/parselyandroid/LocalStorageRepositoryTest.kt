package com.parsely.parselyandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalStorageRepositoryTest {

    private lateinit var sut: LocalStorageRepository
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        sut = LocalStorageRepository(context)
    }

    @Test
    fun `when expelling stored event, then assert that it has no effect`() {
        // given
        sut.persistQueue((1..100).map { mapOf("index" to it) })

        // when
        sut.expelStoredEvent()

        // then
        assertThat(sut.storedQueue).hasSize(100)
    }

    @Test
    fun `given the list of events, when persisting the list, then querying the list returns the same result`() {
        // given
        val eventsList = (1..10).map { mapOf("index" to it) }

        // when
        sut.persistQueue(eventsList)

        // then
        assertThat(sut.storedQueue).hasSize(10).containsExactlyInAnyOrderElementsOf(eventsList)
    }

    @Test
    fun `given no locally stored list, when requesting stored queue, then return an empty list`() {
        assertThat(sut.storedQueue).isEmpty()
    }

    @Test
    fun `given stored queue with some elements, when persisting in-memory queue, then assert there'll be no duplicates and queues will be combined`() {
        // given
        val storedQueue = (1..5).map { mapOf("index" to it) }
        val inMemoryQueue = (3..10).map { mapOf("index" to it) }
        sut.persistQueue(storedQueue)

        // when
        sut.persistQueue(inMemoryQueue)

        // then
        val expectedQueue = (1..10).map { mapOf("index" to it) }
        assertThat(sut.storedQueue).hasSize(10).containsExactlyInAnyOrderElementsOf(expectedQueue)
    }

    @Test
    fun `given stored queue, when purging stored queue, then assert queue is purged`() {
        // given
        val eventsList = (1..10).map { mapOf("index" to it) }
        sut.persistQueue(eventsList)

        // when
        sut.purgeStoredQueue()

        // then
        assertThat(sut.storedQueue).isEmpty()
    }

    @Test
    fun `given stored file with serialized events, when querying the queue, then list has expected events`() {
        // given
        val file = File(context.filesDir.path + "/parsely-events.ser")
        File(ClassLoader.getSystemResource("valid-java-parsely-events.ser")?.path!!).copyTo(file)

        // when
        val queue = sut.storedQueue

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
