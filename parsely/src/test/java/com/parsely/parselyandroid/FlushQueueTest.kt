package com.parsely.parselyandroid

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.SocketException
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
    fun `given a backlog over the event cap, when flushing, then send at most 500 events and keep the rest`() =
        runTest {
            // given
            val repository = FakeRepository().apply { insertEvents(events(1_200)) }
            val restClient = FakeRestClient().apply { nextResult = Result.success(Unit) }
            val sut = FlushQueue(FakeFlushManager(), repository, restClient, this, FakeConnectivityStatusProvider())

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(restClient.eventCounts()).containsExactly(500)
            assertThat(repository.getStoredQueue()).hasSize(700)
        }

    @Test
    fun `given a backlog over the event cap, when flushing repeatedly, then drain it in capped batches`() =
        runTest {
            // given
            val repository = FakeRepository().apply { insertEvents(events(1_200)) }
            val restClient = FakeRestClient().apply { nextResult = Result.success(Unit) }
            val sut = FlushQueue(FakeFlushManager(), repository, restClient, this, FakeConnectivityStatusProvider())

            // when
            repeat(3) {
                sut.invoke(false)
                runCurrent()
            }

            // then
            assertThat(restClient.eventCounts()).containsExactly(500, 500, 200)
            assertThat(repository.getStoredQueue()).isEmpty()
        }

    @Test
    fun `given a backlog over the byte cap, when flushing, then keep the request body within 256 KB`() =
        runTest {
            // given
            val repository = FakeRepository().apply { insertEvents(events(300, bytesEach = 10_000)) }
            val restClient = FakeRestClient().apply { nextResult = Result.success(Unit) }
            val sut = FlushQueue(FakeFlushManager(), repository, restClient, this, FakeConnectivityStatusProvider())

            // when
            sut.invoke(false)
            runCurrent()

            // then
            val body = restClient.payloads.single().toByteArray()
            assertThat(body.size).isLessThanOrEqualTo(256 * 1024)
            assertThat(restClient.eventCounts().single()).isGreaterThan(1)
            assertThat(repository.getStoredQueue()).hasSize(300 - restClient.eventCounts().single())
        }

    @Test
    fun `given a single event over the byte cap, when flushing, then send it alone`() =
        runTest {
            // given
            val repository = FakeRepository().apply { insertEvents(events(1, bytesEach = 300_000) + events(5)) }
            val restClient = FakeRestClient().apply { nextResult = Result.success(Unit) }
            val sut = FlushQueue(FakeFlushManager(), repository, restClient, this, FakeConnectivityStatusProvider())

            // when
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(restClient.eventCounts()).containsExactly(1)
            assertThat(repository.getStoredQueue()).hasSize(5)
        }

    @Test
    fun `given the server rejects a batch as too large, when flushing again, then send half as many events`() =
        runTest {
            // given
            val repository = FakeRepository().apply { insertEvents(events(400)) }
            val restClient = FakeRestClient().apply { nextResult = Result.failure(HttpStatusException(413)) }
            val sut = FlushQueue(FakeFlushManager(), repository, restClient, this, FakeConnectivityStatusProvider())

            // when
            repeat(3) {
                sut.invoke(false)
                runCurrent()
            }

            // then
            assertThat(restClient.eventCounts()).containsExactly(400, 200, 100)
            assertThat(repository.getStoredQueue()).hasSize(400)
        }

    @Test
    fun `given the server rejects a single event as too large, when flushing, then drop it and send the rest next`() =
        runTest {
            // given
            val tooLarge = mapOf("id" to "too large")
            val repository = FakeRepository().apply { insertEvents(listOf(tooLarge) + events(3)) }
            val restClient = FakeRestClient().apply { nextResult = Result.failure(HttpStatusException(413)) }
            val sut = FlushQueue(FakeFlushManager(), repository, restClient, this, FakeConnectivityStatusProvider())
            repeat(3) { // 4 events, then 2, then the lone first event is rejected and dropped
                sut.invoke(false)
                runCurrent()
            }

            // when
            restClient.nextResult = Result.success(Unit)
            sut.invoke(false)
            runCurrent()

            // then
            assertThat(restClient.eventCounts()).containsExactly(4, 2, 1, 3)
            assertThat(repository.getStoredQueue()).isEmpty()
        }

    @Test
    fun `given a split after too large, when a batch then succeeds, then return to the full event cap`() =
        runTest {
            // given
            val repository = FakeRepository().apply { insertEvents(events(1_200)) }
            val restClient = FakeRestClient().apply { nextResult = Result.failure(HttpStatusException(413)) }
            val sut = FlushQueue(FakeFlushManager(), repository, restClient, this, FakeConnectivityStatusProvider())
            sut.invoke(false) // 500 rejected
            runCurrent()

            // when
            restClient.nextResult = Result.success(Unit)
            repeat(2) {
                sut.invoke(false)
                runCurrent()
            }

            // then
            assertThat(restClient.eventCounts()).containsExactly(500, 250, 500)
        }

    @Test
    fun `given the connection resets without a status, when flushing again, then keep the batch size and the events`() =
        runTest {
            // given
            val repository = FakeRepository().apply { insertEvents(events(600)) }
            val restClient = FakeRestClient().apply { nextResult = Result.failure(SocketException("Connection reset")) }
            val sut = FlushQueue(FakeFlushManager(), repository, restClient, this, FakeConnectivityStatusProvider())

            // when
            repeat(2) {
                sut.invoke(false)
                runCurrent()
            }

            // then
            assertThat(restClient.eventCounts()).containsExactly(500, 500)
            assertThat(repository.getStoredQueue()).hasSize(600)
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
        val payloads = mutableListOf<String>()

        override suspend fun send(payload: String): Result<Unit> {
            payloads += payload
            return nextResult!!
        }

        fun eventCounts(): List<Int> = payloads.map { (ObjectMapper().readValue(it, Map::class.java)["events"] as List<*>).size }
    }

    private fun events(count: Int, bytesEach: Int = 0) =
        List(count) { mapOf("id" to it, "padding" to "x".repeat(bytesEach)) }

    private class FakeConnectivityStatusProvider : ConnectivityStatusProvider {
        var reachable = true
        override fun isReachable() = reachable
    }
}
