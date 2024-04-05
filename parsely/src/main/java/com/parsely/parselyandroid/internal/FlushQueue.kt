package com.parsely.parselyandroid.internal

import com.parsely.parselyandroid.internal.JsonSerializer.toParselyEventsPayload
import com.parsely.parselyandroid.internal.Logging.log
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
            log("Network unreachable. Not flushing.")
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
                    log("Debug mode on. Not sending to Parse.ly. Otherwise, would sent ${eventsToSend.size} events: $jsonPayload")
                    repository.remove(eventsToSend)
                    return@launch
                }
                log("Sending request with %d events", eventsToSend.size)
                log("POST Data %s", jsonPayload)
                log("Requested %s", ParselyTrackerInternal.ROOT_URL)
                restClient.send(jsonPayload)
                    .fold(
                        onSuccess = {
                            log("Pixel request success")
                            repository.remove(eventsToSend)
                        },
                        onFailure = {
                            log("Pixel request exception")
                            log(it.toString())
                        }
                    )
            }
        }
    }
}
