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
                        },
                        onFailure = {
                            Log.e("Pixel request exception", it)
                        }
                    )
            }
        }
    }
}
