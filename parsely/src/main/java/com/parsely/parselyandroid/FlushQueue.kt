package com.parsely.parselyandroid

import com.parsely.parselyandroid.JsonSerializer.toParselyEventsPayload
import com.parsely.parselyandroid.Logging.PLog
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
            PLog("Network unreachable. Not flushing.")
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
                    PLog("Debug mode on. Not sending to Parse.ly. Otherwise, would sent ${eventsToSend.size} events")
                    repository.remove(eventsToSend)
                    return@launch
                }
                PLog("Sending request with %d events", eventsToSend.size)
                val jsonPayload = toParselyEventsPayload(eventsToSend)
                PLog("POST Data %s", jsonPayload)
                PLog("Requested %s", ParselyTracker.ROOT_URL)
                restClient.send(jsonPayload)
                    .fold(
                        onSuccess = {
                            PLog("Pixel request success")
                            repository.remove(eventsToSend)
                        },
                        onFailure = {
                            PLog("Pixel request exception")
                            PLog(it.toString())
                        }
                    )
            }
        }
    }
}
