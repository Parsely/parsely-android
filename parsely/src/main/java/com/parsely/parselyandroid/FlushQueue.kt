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
    private val scope: CoroutineScope
) {

    private val mutex = Mutex()

    operator fun invoke(skipSendingEvents: Boolean) {
        scope.launch {
            mutex.withLock {
                val eventsToSend = repository.getStoredQueue()

                if (eventsToSend.isEmpty()) {
                    flushManager.stop()
                    return@launch
                }

                if (skipSendingEvents) {
                    ParselyTracker.PLog("Debug mode on. Not sending to Parse.ly. Otherwise, would sent ${eventsToSend.size} events")
                    repository.remove(eventsToSend)
                } else {
                    ParselyTracker.PLog("Sending request with %d events", eventsToSend.size)
                    val jsonPayload = toParselyEventsPayload(eventsToSend)
                    ParselyTracker.PLog("POST Data %s", jsonPayload)
                    ParselyTracker.PLog("Requested %s", ParselyTracker.ROOT_URL)
                    restClient.send(jsonPayload)
                        .fold(
                            onSuccess = {
                                ParselyTracker.PLog("Pixel request success")
                                repository.remove(eventsToSend)
                                ParselyTracker.PLog("Event queue empty, flush timer cleared.")
                                if (repository.getStoredQueue().isEmpty()) {
                                    flushManager.stop()
                                }
                            },
                            onFailure = {
                                ParselyTracker.PLog("Pixel request exception")
                                ParselyTracker.PLog(it.toString())
                            }
                        )
                }
            }
        }
    }
}
