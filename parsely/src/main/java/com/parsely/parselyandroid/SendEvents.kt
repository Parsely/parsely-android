package com.parsely.parselyandroid

import com.parsely.parselyandroid.JsonSerializer.toParselyEventsPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SendEvents(
    private val flushManager: FlushManager,
    private val localStorageRepository: QueueRepository,
    private val parselyAPIConnection: ParselyAPIConnection,
    private val scope: CoroutineScope
) {

    private val mutex = Mutex()

    operator fun invoke(isDebug: Boolean) {
        scope.launch {
            mutex.withLock {
                val eventsToSend = localStorageRepository.getStoredQueue()

                if (eventsToSend.isEmpty()) {
                    flushManager.stop()
                    return@launch
                }
                ParselyTracker.PLog("Sending request with %d events", eventsToSend.size)

                val jsonPayload = toParselyEventsPayload(eventsToSend)

                ParselyTracker.PLog("POST Data %s", jsonPayload)

                if (isDebug) {
                    ParselyTracker.PLog("Debug mode on. Not sending to Parse.ly")
                    localStorageRepository.remove(eventsToSend)
                } else {
                    ParselyTracker.PLog("Requested %s", ParselyTracker.ROOT_URL)
                    parselyAPIConnection.send(jsonPayload)
                        .fold(
                            onSuccess = {
                                ParselyTracker.PLog("Pixel request success")
                                localStorageRepository.remove(eventsToSend)
                                ParselyTracker.PLog("Event queue empty, flush timer cleared.")
                                if (localStorageRepository.getStoredQueue().isEmpty()) {
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
