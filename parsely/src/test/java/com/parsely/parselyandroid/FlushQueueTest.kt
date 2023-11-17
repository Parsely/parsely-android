package com.parsely.parselyandroid

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FlushQueueTest {

    private lateinit var sut: FlushQueue

    @Test
    fun `given empty local storage, when sending events, then do nothing`() =
        runTest {
            // given
            sut = FlushQueue(
                FakeFlushManager(),
                FakeRepository(),
                FakeRestClient(),
                this
            )

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(FakeRepository().getStoredQueue()).isEmpty()
        }

    @Test
    fun `given non-empty local storage, when flushing queue with not skipping sending events, then events are sent and removed from local storage`() =
        runTest {
            // given
            val repository = FakeRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            val parselyAPIConnection = FakeRestClient().apply {
                nextResult = Result.success(Unit)
            }
            sut = FlushQueue(
                FakeFlushManager(),
                repository,
                parselyAPIConnection,
                this
            )

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(repository.getStoredQueue()).isEmpty()
        }

    @Test
    fun `given non-empty local storage, when flushing queue with skipping sending events, then events are not sent and removed from local storage`() =
        runTest {
            // given
            val repository = FakeRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            sut = FlushQueue(
                FakeFlushManager(),
                repository,
                FakeRestClient(),
                this
            )

            // when
            sut.invoke(true)
            runCurrent()

            // then
            assertThat(repository.getStoredQueue()).isEmpty()
        }

    @Test
    fun `given non-empty local storage, when flushing queue with not skipping sending events fails, then events are not removed from local storage`() =
        runTest {
            // given
            val repository = FakeRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            val parselyAPIConnection = FakeRestClient().apply {
                nextResult = Result.failure(Exception())
            }
            sut = FlushQueue(
                FakeFlushManager(),
                repository,
                parselyAPIConnection,
                this
            )

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(repository.getStoredQueue()).isNotEmpty
        }

    @Test
    fun `given non-empty local storage, when flushing queue with not skipping sending events, then flush manager is stopped`() =
        runTest {
            // given
            val flushManager = FakeFlushManager()
            val repository = FakeRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            val parselyAPIConnection = FakeRestClient().apply {
                nextResult = Result.success(Unit)
            }
            sut = FlushQueue(
                flushManager,
                repository,
                parselyAPIConnection,
                this
            )

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(flushManager.stopped).isTrue
        }

    @Test
    fun `given non-empty local storage, when flushing queue with not skipping sending events fails, then flush manager is not stopped`() =
        runTest {
            // given
            val flushManager = FakeFlushManager()
            val repository = FakeRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            val parselyAPIConnection = FakeRestClient().apply {
                nextResult = Result.failure(Exception())
            }
            sut = FlushQueue(
                flushManager,
                repository,
                parselyAPIConnection,
                this
            )

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(flushManager.stopped).isFalse
        }

    @Test
    fun `given non-empty local storage, when storage is not empty after successful flushing queue with not skipping sending events, then flush manager is not stopped`() =
        runTest {
            // given
            val flushManager = FakeFlushManager()
            val repository = object : FakeRepository() {
                override suspend fun getStoredQueue(): ArrayList<Map<String, Any?>?> {
                    return ArrayList(listOf(mapOf("test" to 123)))
                }
            }
            val parselyAPIConnection = FakeRestClient().apply {
                nextResult = Result.success(Unit)
            }
            sut = FlushQueue(
                flushManager,
                repository,
                parselyAPIConnection,
                this
            )

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(flushManager.stopped).isFalse
        }

    @Test
    fun `given empty local storage, when invoked, then flush manager is stopped`() = runTest {
        // given
        val flushManager = FakeFlushManager()
        sut = FlushQueue(
            flushManager,
            FakeRepository(),
            FakeRestClient(),
            this
        )

        // when
        sut.invoke(false)
        runCurrent()

        // then
        assertThat(flushManager.stopped).isTrue()
    }

    private class FakeFlushManager : FlushManager {
        var stopped = false
        override fun start() {
            TODO("Not implemented")
        }

        override fun stop() {
            stopped = true
        }

        override val isRunning
            get() = TODO("Not implemented")
        override val intervalMillis
            get() = TODO("Not implemented")
    }

    private open class FakeRepository : QueueRepository {
        private var storage = emptyList<Map<String, Any?>?>()

        override suspend fun insertEvents(toInsert: List<Map<String, Any?>?>) {
            storage = storage + toInsert
        }

        override suspend fun remove(toRemove: List<Map<String, Any?>?>) {
            storage = storage - toRemove.toSet()
        }

        override suspend fun getStoredQueue(): ArrayList<Map<String, Any?>?> {
            return ArrayList(storage)
        }
    }

    private class FakeRestClient : RestClient {

        var nextResult: Result<Unit>? = null

        override suspend fun send(payload: String): Result<Unit> {
            return nextResult!!
        }
    }
}
