package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
internal class InMemoryBufferTest {

    private lateinit var sut: InMemoryBuffer
    private val repository = FakeLocalStorageRepository()

    @Test
    fun `when adding a new event, then save it to local storage`() = runTest {
        // given
        val event = mapOf("test" to 123)
        sut = InMemoryBuffer(backgroundScope, repository) { }

        // when
        sut.add(event)
        advanceTimeBy(1.seconds)
        runCurrent()
        backgroundScope.cancel()

        // then
        assertThat(repository.getStoredQueue()).containsOnlyOnce(event)
    }

    @Test
    fun `given an onEventAdded listener, when adding a new event, then run the onEventAdded listener`() = runTest {
        // given
        val event = mapOf("test" to 123)
        var onEventAddedExecuted = false
        sut = InMemoryBuffer(backgroundScope, repository) { onEventAddedExecuted = true }

        // when
        sut.add(event)
        advanceTimeBy(1.seconds)
        runCurrent()
        backgroundScope.cancel()

        // then
        assertThat(onEventAddedExecuted).isTrue
    }

    @Test
    fun `when adding multiple events in different intervals, then save all of them to local storage without duplicates`() =
        runTest {
            // given
            val events = (0..2).map { mapOf("test" to it) }
            sut = InMemoryBuffer(backgroundScope, repository) {}

            // when
            sut.add(events[0])
            advanceTimeBy(1.seconds)
            runCurrent()

            sut.add(events[1])
            advanceTimeBy(0.5.seconds)
            runCurrent()

            sut.add(events[2])
            advanceTimeBy(0.5.seconds)
            runCurrent()

            backgroundScope.cancel()

            // then
            assertThat(repository.getStoredQueue()).containsOnlyOnceElementsOf(events)
        }

    class FakeLocalStorageRepository :
        LocalStorageRepository(ApplicationProvider.getApplicationContext()) {

        private val events = mutableListOf<Map<String, Any?>?>()

        override suspend fun insertEvents(toInsert: List<Map<String, Any?>?>) {
            events.addAll(toInsert)
        }

        override suspend fun getStoredQueue(): ArrayList<Map<String, Any?>?> {
            return ArrayList(events)
        }
    }
}
