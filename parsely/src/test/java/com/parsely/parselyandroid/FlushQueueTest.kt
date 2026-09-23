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

    private val DEFAULT_HOSTS = PixelHosts(mapOf("a.com" to "p1.parsely.com"))

    @Test
    fun `given empty local storage, when sending events, then do nothing`() =
        runTest {
            // given
            val sut = FlushQueue(
                FakeFlushManager(),
                FakeRepository(),
                FakeRestClient(),
                DEFAULT_HOSTS,
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
                insertEvents(listOf(mapOf("idsite" to "a.com", "test" to 123)))
            }
            val parselyAPIConnection = FakeRestClient().apply {
                nextResult = Result.success(Unit)
            }
            val sut = FlushQueue(
                FakeFlushManager(),
                repository,
                parselyAPIConnection,
                DEFAULT_HOSTS,
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
                insertEvents(listOf(mapOf("idsite" to "a.com", "test" to 123)))
            }
            val sut = FlushQueue(
                FakeFlushManager(),
                repository,
                FakeRestClient(),
                DEFAULT_HOSTS,
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
                insertEvents(listOf(mapOf("idsite" to "a.com", "test" to 123)))
            }
            val parselyAPIConnection = FakeRestClient().apply {
                nextResult = Result.failure(Exception())
            }
            val sut = FlushQueue(
                FakeFlushManager(),
                repository,
                parselyAPIConnection,
                DEFAULT_HOSTS,
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
                insertEvents(listOf(mapOf("idsite" to "a.com", "test" to 123)))
            }
            val parselyAPIConnection = FakeRestClient().apply {
                nextResult = Result.failure(Exception())
            }
            val sut = FlushQueue(
                flushManager,
                repository,
                parselyAPIConnection,
                DEFAULT_HOSTS,
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
                    return ArrayList(listOf(mapOf("idsite" to "a.com", "test" to 123)))
                }
            }
            val parselyAPIConnection = FakeRestClient().apply {
                nextResult = Result.success(Unit)
            }
            val sut = FlushQueue(
                flushManager,
                repository,
                parselyAPIConnection,
                DEFAULT_HOSTS,
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
            DEFAULT_HOSTS,
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
                insertEvents(listOf(mapOf("idsite" to "a.com", "test" to 123)))
            }
            val sut = FlushQueue(
                FakeFlushManager(),
                repository,
                FakeRestClient(),
                DEFAULT_HOSTS,
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
    fun `given site ids sharing a host, when flushing, then send one request`() = runTest {
        // given
        val repository = FakeRepository().apply {
            insertEvents(listOf(mapOf("idsite" to "a.com"), mapOf("idsite" to "b.com")))
        }
        val restClient = FakeRestClient().apply { nextResult = Result.success(Unit) }
        val sut = FlushQueue(
            FakeFlushManager(),
            repository,
            restClient,
            PixelHosts(mapOf("a.com" to "p1.parsely.com", "b.com" to "p1.parsely.com")),
            this,
            FakeConnectivityStatusProvider()
        )

        // when
        sut.invoke(false)
        runCurrent()

        // then
        assertThat(restClient.sentUrls).containsExactly("https://p1.parsely.com/mobileproxy")
        assertThat(repository.getStoredQueue()).isEmpty()
    }

    @Test
    fun `given site ids on different hosts, when flushing, then send one request per host`() = runTest {
        // given
        val repository = FakeRepository().apply {
            insertEvents(listOf(mapOf("idsite" to "a.com"), mapOf("idsite" to "c.com")))
        }
        val restClient = FakeRestClient().apply { nextResult = Result.success(Unit) }
        val sut = FlushQueue(
            FakeFlushManager(),
            repository,
            restClient,
            PixelHosts(mapOf("a.com" to "p1.parsely.com", "c.com" to "p1-irl.parsely.com")),
            this,
            FakeConnectivityStatusProvider()
        )

        // when
        sut.invoke(false)
        runCurrent()

        // then
        assertThat(restClient.sentUrls).containsExactlyInAnyOrder(
            "https://p1.parsely.com/mobileproxy",
            "https://p1-irl.parsely.com/mobileproxy"
        )
        assertThat(repository.getStoredQueue()).isEmpty()
    }

    @Test
    fun `given one host failing, when flushing, then the other host's events are still removed`() = runTest {
        // given
        val repository = FakeRepository().apply {
            insertEvents(listOf(mapOf("idsite" to "a.com"), mapOf("idsite" to "c.com")))
        }
        val restClient = FakeRestClient().apply {
            resultsByUrl = mapOf(
                "https://p1.parsely.com/mobileproxy" to Result.success(Unit),
                "https://p1-irl.parsely.com/mobileproxy" to Result.failure(Exception()),
            )
        }
        val sut = FlushQueue(
            FakeFlushManager(),
            repository,
            restClient,
            PixelHosts(mapOf("a.com" to "p1.parsely.com", "c.com" to "p1-irl.parsely.com")),
            this,
            FakeConnectivityStatusProvider()
        )

        // when
        sut.invoke(false)
        runCurrent()

        // then
        assertThat(repository.getStoredQueue()).containsExactly(mapOf("idsite" to "c.com"))
    }

    @Test
    fun `given an undeclared site id, when flushing, then drop it from storage and send nothing for it`() = runTest {
        // given: the stored queue is disk-backed, so merely skipping would accumulate forever
        val repository = FakeRepository().apply {
            insertEvents(listOf(mapOf("idsite" to "a.com"), mapOf("idsite" to "undeclared.com")))
        }
        val restClient = FakeRestClient().apply { nextResult = Result.success(Unit) }
        val sut = FlushQueue(
            FakeFlushManager(),
            repository,
            restClient,
            PixelHosts(mapOf("a.com" to "p1.parsely.com")),
            this,
            FakeConnectivityStatusProvider()
        )

        // when
        sut.invoke(false)
        runCurrent()

        // then
        assertThat(restClient.sentUrls).containsExactly("https://p1.parsely.com/mobileproxy")
        assertThat(repository.getStoredQueue()).isEmpty()
    }

    @Test
    fun `given an empty host map, when flushing, then send nothing and drain storage`() = runTest {
        // given
        val repository = FakeRepository().apply {
            insertEvents(listOf(mapOf("idsite" to "a.com")))
        }
        val restClient = FakeRestClient()
        val sut = FlushQueue(
            FakeFlushManager(),
            repository,
            restClient,
            PixelHosts(emptyMap()),
            this,
            FakeConnectivityStatusProvider()
        )

        // when
        sut.invoke(false)
        runCurrent()

        // then
        assertThat(restClient.sentUrls).isEmpty()
        assertThat(repository.getStoredQueue()).isEmpty()
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
        var resultsByUrl: Map<String, Result<Unit>> = emptyMap()
        val sentUrls = mutableListOf<String>()

        override suspend fun send(url: String, payload: String): Result<Unit> {
            sentUrls += url
            return resultsByUrl[url] ?: nextResult!!
        }
    }

    private class FakeConnectivityStatusProvider : ConnectivityStatusProvider {
        var reachable = true
        override fun isReachable() = reachable
    }
}
