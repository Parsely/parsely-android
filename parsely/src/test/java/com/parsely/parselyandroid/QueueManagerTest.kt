package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
internal class QueueManagerTest {

    private lateinit var sut: QueueManager

    private val repository = FakeLocalRepository()

    @Test
    fun `when adding an event, then persist it in local storage and call onEventAdded`() = runTest {
        // given
        sut = QueueManager(repository, this) {
            FakeOnEventAdded.fakeOnEventAdded()
        }
        val testEvent = mapOf("test" to 123)

        // when
        sut.addEvent(testEvent)
        runCurrent()

        // then
        assertThat(FakeOnEventAdded.onEventAdded).isTrue
        assertThat(repository.getStoredQueue()).containsExactly(testEvent)
    }

    object FakeOnEventAdded {
        var onEventAdded = false

        fun fakeOnEventAdded() {
            onEventAdded = true
        }
    }


    class FakeLocalRepository :
        LocalStorageRepository(ApplicationProvider.getApplicationContext()) {

        private var localFileQueue = emptyList<Map<String, Any?>?>()

        override fun persistEvent(event: Map<String, Any?>) {
            this.localFileQueue += event
        }

        override fun getStoredQueue() = ArrayList(localFileQueue)
    }
}
