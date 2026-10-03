package com.parsely.parselyandroid

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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

    @Test
    fun `given empty local storage, when sending events, then do nothing`() =
        runTest {
            // given
            val sut = FlushQueue(
                FakeFlushManager(),
                FakeRepository(),
                FakeRestClient(),
                this,
                FakeConnectivityStatusProvider()
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
            val sut = FlushQueue(
                FakeFlushManager(),
                repository,
                parselyAPIConnection,
                this,
                FakeConnectivityStatusProvider()
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
            val sut = FlushQueue(
                FakeFlushManager(),
                repository,
                FakeRestClient(),
                this,
                FakeConnectivityStatusProvider()
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
            val sut = FlushQueue(
                FakeFlushManager(),
                repository,
                parselyAPIConnection,
                this,
                FakeConnectivityStatusProvider()
            )

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(repository.getStoredQueue()).isNotEmpty
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
            val sut = FlushQueue(
                flushManager,
                repository,
                parselyAPIConnection,
                this,
                FakeConnectivityStatusProvider()
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
            val sut = FlushQueue(
                flushManager,
                repository,
                parselyAPIConnection,
                this,
                FakeConnectivityStatusProvider()
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
        val sut = FlushQueue(
            flushManager,
            FakeRepository(),
            FakeRestClient(),
            this,
            FakeConnectivityStatusProvider()
        )

        // when
        sut.invoke(false)
        runCurrent()

        // then
        assertThat(flushManager.stopped).isTrue()
    }

    @Test
    fun `given non-empty local storage, when flushing queue with no internet connection, then events are not sent and not removed from local storage`() =
        runTest {
            // given
            val repository = FakeRepository().apply {
                insertEvents(listOf(mapOf("test" to 123)))
            }
            val sut = FlushQueue(
                FakeFlushManager(),
                repository,
                FakeRestClient(),
                this,
                FakeConnectivityStatusProvider().apply { reachable = false }
            )

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(repository.getStoredQueue()).isNotEmpty
        }


    @Test
    fun `given a failed request, when flushing again before the backoff passes, then do not send`() =
        runTest {
            // given
            val clock = FakeClock()
            val restClient = FakeRestClient().apply { nextResult = Result.failure(Exception()) }
            val sut = FlushQueue(
                FakeFlushManager(),
                FakeRepository().apply { insertEvents(listOf(mapOf("test" to 123))) },
                restClient,
                this,
                FakeConnectivityStatusProvider(),
                clock,
            ) { 1.0 }
            sut.invoke(false)
            runCurrent()

            // when
            clock.nowMillis = 119_999
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(restClient.sendCount).isEqualTo(1)
        }

    @Test
    fun `given a failed request, when flushing after the backoff passes, then send again`() =
        runTest {
            // given
            val clock = FakeClock()
            val restClient = FakeRestClient().apply { nextResult = Result.failure(Exception()) }
            val sut = FlushQueue(
                FakeFlushManager(),
                FakeRepository().apply { insertEvents(listOf(mapOf("test" to 123))) },
                restClient,
                this,
                FakeConnectivityStatusProvider(),
                clock,
            ) { 1.0 }
            sut.invoke(false)
            runCurrent()

            // when
            clock.nowMillis = 120_000
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(restClient.sendCount).isEqualTo(2)
        }

    @Test
    fun `given requests keep failing, when flushing every second, then double the wait after each failure up to 15 minutes`() =
        runTest {
            // given
            val clock = FakeClock()
            val restClient = FakeRestClient().apply { nextResult = Result.failure(Exception()) }
            val sut = FlushQueue(
                FakeFlushManager(),
                FakeRepository().apply { insertEvents(listOf(mapOf("test" to 123))) },
                restClient,
                this,
                FakeConnectivityStatusProvider(),
                clock,
            ) { 1.0 }

            // when
            val attemptSeconds = mutableListOf<Long>()
            for (second in 0..3600L) {
                clock.nowMillis = second * 1000
                val sentBefore = restClient.sendCount
                sut.invoke(false)
                runCurrent()
                if (restClient.sendCount > sentBefore) attemptSeconds += second
            }

            // then
            // waits of 2, 4 and 8 flush intervals (60 s), then the 15-minute cap
            assertThat(attemptSeconds).containsExactly(0, 120, 360, 840, 1740, 2640, 3540)
        }

    @Test
    fun `given jitter, when a request fails, then scale the backoff by it`() =
        runTest {
            // given
            val clock = FakeClock()
            val restClient = FakeRestClient().apply { nextResult = Result.failure(Exception()) }
            val sut = FlushQueue(
                FakeFlushManager(),
                FakeRepository().apply { insertEvents(listOf(mapOf("test" to 123))) },
                restClient,
                this,
                FakeConnectivityStatusProvider(),
                clock,
            ) { 0.8 }
            sut.invoke(false)
            runCurrent()

            // when
            clock.nowMillis = 95_999
            sut.invoke(false)
            runCurrent()
            val sentBeforeBackoffEnds = restClient.sendCount
            clock.nowMillis = 96_000
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(sentBeforeBackoffEnds).isEqualTo(1)
            assertThat(restClient.sendCount).isEqualTo(2)
        }

    @Test
    fun `given failures followed by a success, when the next request fails, then start the backoff over`() =
        runTest {
            // given
            val clock = FakeClock()
            val repository = FakeRepository().apply { insertEvents(listOf(mapOf("test" to 123))) }
            val restClient = FakeRestClient().apply { nextResult = Result.failure(Exception()) }
            val sut = FlushQueue(
                FakeFlushManager(),
                repository,
                restClient,
                this,
                FakeConnectivityStatusProvider(),
                clock,
            ) { 1.0 }
            sut.invoke(false) // fails at 0 s, next attempt at 120 s
            runCurrent()
            clock.nowMillis = 120_000
            sut.invoke(false) // fails again, next attempt at 360 s
            runCurrent()
            clock.nowMillis = 360_000
            restClient.nextResult = Result.success(Unit)
            sut.invoke(false) // succeeds
            runCurrent()
            repository.insertEvents(listOf(mapOf("test" to 456)))

            // when
            clock.nowMillis = 420_000
            restClient.nextResult = Result.failure(Exception())
            sut.invoke(false) // fails, next attempt at 540 s rather than after a longer wait
            runCurrent()
            clock.nowMillis = 540_000
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(restClient.sendCount).isEqualTo(5)
        }

    @Test
    fun `given a clock that reads below zero, when flushing for the first time, then send`() =
        runTest {
            // given
            val clock = FakeClock().apply { nowMillis = -5_000 }
            val restClient = FakeRestClient().apply { nextResult = Result.success(Unit) }
            val sut = FlushQueue(
                FakeFlushManager(),
                FakeRepository().apply { insertEvents(listOf(mapOf("test" to 123))) },
                restClient,
                this,
                FakeConnectivityStatusProvider(),
                clock,
            ) { 1.0 }

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(restClient.sendCount).isEqualTo(1)
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
        override val intervalMillis = 60_000L
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
        var sendCount = 0

        override suspend fun send(payload: String): Result<Unit> {
            sendCount++
            return nextResult!!
        }
    }

    private class FakeClock : Clock() {
        var nowMillis = 0L

        override val now: Duration
            get() = nowMillis.milliseconds
    }

    private class FakeConnectivityStatusProvider : ConnectivityStatusProvider {
        var reachable = true
        override fun isReachable() = reachable
    }
}
