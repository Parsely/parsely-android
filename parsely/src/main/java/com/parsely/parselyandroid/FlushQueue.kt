package com.parsely.parselyandroid

import com.parsely.parselyandroid.JsonSerializer.toParselyEventsPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FlushQueue(
    private val flushManager: FlushManager,
    private val repository: QueueRepository,
    private val restClient: RestClient,
    private val scope: CoroutineScope,
    private val connectivityStatusProvider: ConnectivityStatusProvider
) {

    private val mutex = Mutex()

    // Guarded by mutex.
    private var batchLimit = MAX_BATCH_EVENTS

    operator fun invoke(skipSendingEvents: Boolean) {
        if (!connectivityStatusProvider.isReachable()) {
            Log.d("Network unreachable. Not flushing.")
            return
        }
        scope.launch {
            mutex.withLock {
                val eventsToSend = repository.getStoredQueue()

                if (eventsToSend.isEmpty()) {
                    flushManager.stop()
                    return@launch
                }

                if (skipSendingEvents) {
                    Log.d("Debug mode on. Not sending to Parse.ly. Otherwise, would sent ${eventsToSend.size} events: ${toParselyEventsPayload(eventsToSend)}")
                    repository.remove(eventsToSend)
                    return@launch
                }
                val (batch, jsonPayload) = nextBatch(eventsToSend)
                Log.d("Sending request with ${batch.size} of ${eventsToSend.size} queued events")
                Log.d("POST Data $jsonPayload")
                Log.d("Requested ${ParselyTrackerInternal.ROOT_URL}")
                restClient.send(jsonPayload)
                    .fold(
                        onSuccess = {
                            Log.i("Pixel request success")
                            repository.remove(batch)
                            batchLimit = MAX_BATCH_EVENTS
                        },
                        onFailure = {
                            Log.e("Pixel request exception", it)
                            if (it is HttpStatusException && it.status == HTTP_PAYLOAD_TOO_LARGE) {
                                shrinkAfterTooLarge(batch)
                            }
                        }
                    )
            }
        }
    }

    /** The first queued events that fit under [batchLimit] and [MAX_BATCH_BYTES], always at least one. */
    private fun nextBatch(queue: List<Map<String, Any?>?>): Pair<List<Map<String, Any?>?>, String> {
        var batch = queue.take(batchLimit)
        var payload = toParselyEventsPayload(batch)
        while (batch.size > 1 && payload.toByteArray().size > MAX_BATCH_BYTES) {
            batch = batch.take(batch.size / 2)
            payload = toParselyEventsPayload(batch)
        }
        return batch to payload
    }

    private suspend fun shrinkAfterTooLarge(rejected: List<Map<String, Any?>?>) {
        if (rejected.size == 1) {
            // Resending it can never succeed, and it was what made the larger batches too large.
            Log.e("Dropping an event the server rejected as too large")
            repository.remove(rejected)
            batchLimit = MAX_BATCH_EVENTS
        } else {
            batchLimit = rejected.size / 2
        }
    }

    internal companion object {
        const val MAX_BATCH_EVENTS = 500
        // Well under the collection endpoint's 5 MB body limit, so a backlog never becomes unsendable.
        const val MAX_BATCH_BYTES = 256 * 1024
        private const val HTTP_PAYLOAD_TOO_LARGE = 413
    }
}
