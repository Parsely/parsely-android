package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalStorageRepositoryTest {

    private lateinit var sut: LocalStorageRepository

    @Before
    fun setUp() {
        sut = LocalStorageRepository(ApplicationProvider.getApplicationContext())
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
}
