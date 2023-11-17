package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SendEventsTest {

    private lateinit var sut: SendEvents

    @Test
    fun `given empty local storage, when sending events, then do nothing`() =
        runTest {
            // given
            sut = SendEvents(
                FakeFlushManager(),
                FakeLocalStorageRepository(),
                FakeParselyAPIConnection(),
                this
            )

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(FakeLocalStorageRepository().getStoredQueue()).isEmpty()
        }

    @Test
    fun `given non-empty local storage and debug mode off, when sending events, then events are sent and removed from local storage`() =
        runTest {
            // given
            val repository = FakeLocalStorageRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            val parselyAPIConnection = FakeParselyAPIConnection().apply {
                nextResult = Result.success(Unit)
            }
            sut = SendEvents(
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
    fun `given non-empty local storage and debug mode on, when sending events, then events are not sent and removed from local storage`() =
        runTest {
            // given
            val repository = FakeLocalStorageRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            sut = SendEvents(
                FakeFlushManager(),
                repository,
                FakeParselyAPIConnection(),
                this
            )

            // when
            sut.invoke(true)
            runCurrent()

            // then
            assertThat(repository.getStoredQueue()).isEmpty()
        }

    @Test
    fun `given non-empty local storage and debug mode off, when sending events fails, then events are not removed from local storage`() =
        runTest {
            // given
            val repository = FakeLocalStorageRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            val parselyAPIConnection = FakeParselyAPIConnection().apply {
                nextResult = Result.failure(Exception())
            }
            sut = SendEvents(
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
    fun `given non-empty local storage and debug mode off, when sending events, then flush manager is stopped`() =
        runTest {
            // given
            val flushManager = FakeFlushManager()
            val repository = FakeLocalStorageRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            val parselyAPIConnection = FakeParselyAPIConnection().apply {
                nextResult = Result.success(Unit)
            }
            sut = SendEvents(
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
    fun `given non-empty local storage and debug mode off, when sending events fails, then flush manager is not stopped`() =
        runTest {
            // given
            val flushManager = FakeFlushManager()
            val repository = FakeLocalStorageRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            val parselyAPIConnection = FakeParselyAPIConnection().apply {
                nextResult = Result.failure(Exception())
            }
            sut = SendEvents(
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
    fun `given non-empty local storage and debug mode off, when storage is not empty after successful event, then flush manager is not stopped`() =
        runTest {
            // given
            val flushManager = FakeFlushManager()
            val repository = object : FakeLocalStorageRepository() {
                override suspend fun getStoredQueue(): ArrayList<Map<String, Any?>?> {
                    return ArrayList(listOf(mapOf("test" to 123)))
                }
            }
            val parselyAPIConnection = FakeParselyAPIConnection().apply {
                nextResult = Result.success(Unit)
            }
            sut = SendEvents(
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
        sut = SendEvents(
            flushManager,
            FakeLocalStorageRepository(),
            FakeParselyAPIConnection(),
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

    private class FakeLocalStorageRepository : QueueRepository {
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

    private class FakeParselyAPIConnection : ParselyAPIConnection("") {

        var nextResult: Result<Unit>? = null

        override suspend fun send(payload: String): Result<Unit> {
            return nextResult!!
        }
    }
}
