package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import com.parsely.parselyandroid.QueueManager.QUEUE_SIZE_LIMIT
import com.parsely.parselyandroid.QueueManager.STORAGE_SIZE_LIMIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper.shadowMainLooper

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
internal class QueueManagerTest {

    private lateinit var sut: QueueManager

    private val tracker = FakeTracker()
    private val repository = FakeLocalRepository()

    @Before
    fun setUp() {
        sut = QueueManager(tracker, repository)
    }

    @Test
    fun `given the queue is smaller than any threshold, when querying flush manager, do nothing`() {
        // given
        val initialInMemoryQueue = listOf(mapOf("test" to "test"))
        tracker.applyFakeQueue(initialInMemoryQueue)

        // when
        sut.execute().get()
        shadowMainLooper().idle();

        // then
        assertThat(tracker.inMemoryQueue).isEqualTo(initialInMemoryQueue)
        assertThat(repository.storedQueue).isEmpty()
    }

    @Test
    fun `given the in-memory queue is above the in-memory limit, when querying flush manager, then save queue to local storage and remove first event`() {
        // given
        val initialInMemoryQueue = (1..QUEUE_SIZE_LIMIT + 1).map { mapOf("test" to it) }
        tracker.applyFakeQueue(initialInMemoryQueue)

        // when
        sut.execute().get()
        shadowMainLooper().idle();

        // then
        assertThat(repository.storedQueue).isEqualTo(initialInMemoryQueue)
        assertThat(tracker.inMemoryQueue).hasSize(QUEUE_SIZE_LIMIT)
    }

    @Test
    fun `given the in-memory queue is above the in-memory limit and stored events queue is above stored-queue limit, when querying flush manager, then expel the last event from local storage`() {
        // given
        val initialInMemoryQueue = (1..QUEUE_SIZE_LIMIT + 1).map { mapOf("in memory" to it) }
        tracker.applyFakeQueue(initialInMemoryQueue)
        val initialStoredQueue = (1..STORAGE_SIZE_LIMIT + 1).map { mapOf("storage" to it) }
        repository.persistQueue(initialStoredQueue)

        // when
        sut.execute().get()
        shadowMainLooper().idle();

        // then
        assertThat(repository.wasEventExpelled).isTrue
    }

    inner class FakeTracker : ParselyTracker(
        "siteId", 10, ApplicationProvider.getApplicationContext()
    ) {

        private var fakeQueue: List<Map<String, Any>> = emptyList()

        internal override fun getInMemoryQueue(): List<Map<String, Any>> = fakeQueue

        fun applyFakeQueue(fakeQueue: List<Map<String, Any>>) {
            this.fakeQueue = fakeQueue.toList()
        }

        override fun storedEventsCount(): Int {
            return repository.storedQueue.size
        }
    }

    class FakeLocalRepository :
        LocalStorageRepository(ApplicationProvider.getApplicationContext()) {

        private var localFileQueue = emptyList<Map<String, Any?>?>()
        var wasEventExpelled = false

        override fun persistQueue(inMemoryQueue: List<Map<String, Any?>?>) {
            this.localFileQueue += inMemoryQueue
        }

        override val storedQueue: ArrayList<Map<String, Any?>?>
            get() = ArrayList(localFileQueue)


        override fun expelStoredEvent() {
            wasEventExpelled = true
        }
    }
}
