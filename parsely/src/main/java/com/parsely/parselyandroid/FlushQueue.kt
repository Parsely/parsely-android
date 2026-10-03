package com.parsely.parselyandroid

import com.parsely.parselyandroid.JsonSerializer.toParselyEventsPayload
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FlushQueue(
    private val flushManager: FlushManager,
    private val repository: QueueRepository,
    private val restClient: RestClient,
    private val scope: CoroutineScope,
    private val connectivityStatusProvider: ConnectivityStatusProvider,
    private val clock: Clock = Clock(),
    private val jitter: () -> Double = { Random.nextDouble(MIN_JITTER, MAX_JITTER) },
) {

    private val mutex = Mutex()

    // Guarded by mutex.
    private var consecutiveFailures = 0
    private var nextAttemptAtMillis = 0L

    operator fun invoke(skipSendingEvents: Boolean) {
        if (!connectivityStatusProvider.isReachable()) {
            Log.d("Network unreachable. Not flushing.")
            return
        }
        scope.launch {
            mutex.withLock {
                val now = clock.now.inWholeMilliseconds
                if (now < nextAttemptAtMillis) {
                    Log.d("Backing off after $consecutiveFailures failed requests. Next attempt in ${(nextAttemptAtMillis - now) / 1000} s")
                    return@launch
                }

                val eventsToSend = repository.getStoredQueue()

                if (eventsToSend.isEmpty()) {
                    flushManager.stop()
                    return@launch
                }

                val jsonPayload = toParselyEventsPayload(eventsToSend)
                if (skipSendingEvents) {
                    Log.d("Debug mode on. Not sending to Parse.ly. Otherwise, would sent ${eventsToSend.size} events: $jsonPayload")
                    repository.remove(eventsToSend)
                    return@launch
                }
                Log.d("Sending request with ${eventsToSend.size} events")
                Log.d("POST Data $jsonPayload")
                Log.d("Requested ${ParselyTrackerInternal.ROOT_URL}")
                restClient.send(jsonPayload)
                    .fold(
                        onSuccess = {
                            Log.i("Pixel request success")
                            repository.remove(eventsToSend)
                            resetBackoff()
                        },
                        onFailure = {
                            Log.e("Pixel request exception", it)
                            backOff()
                        }
                    )
            }
        }
    }

    private fun resetBackoff() {
        consecutiveFailures = 0
        nextAttemptAtMillis = 0
    }

    /** Waits twice as long after each consecutive failure, up to [MAX_BACKOFF_MILLIS], then jitters. */
    private fun backOff() {
        consecutiveFailures++
        val exponential = flushManager.intervalMillis * 2.0.pow(consecutiveFailures.coerceAtMost(MAX_EXPONENT))
        val backoff = (min(exponential, MAX_BACKOFF_MILLIS.toDouble()) * jitter()).toLong()
        nextAttemptAtMillis = clock.now.inWholeMilliseconds + backoff
    }

    internal companion object {
        const val MAX_BACKOFF_MILLIS = 15 * 60 * 1000L
        const val MIN_JITTER = 0.8
        const val MAX_JITTER = 1.2
        private const val MAX_EXPONENT = 16
    }
}
