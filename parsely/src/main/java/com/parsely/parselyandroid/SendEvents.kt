package com.parsely.parselyandroid

import com.parsely.parselyandroid.JsonSerializer.toJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SendEvents(
    private val parselyTracker: ParselyTracker,
    private val localStorageRepository: LocalStorageRepository,
    private val parselyAPIConnection: ParselyAPIConnection,
    private val scope: CoroutineScope
) {

    operator fun invoke(isDebug: Boolean) {
        scope.launch {
            val eventsToSend = localStorageRepository.getStoredQueue()

            if (eventsToSend.isEmpty()) {
                return@launch
            }
            ParselyTracker.PLog("Sending request with %d events", eventsToSend.size)

            val batchMap: MutableMap<String, Any> = HashMap()
            batchMap["events"] = eventsToSend
            val jsonPayload = toJson(batchMap).orEmpty()

            ParselyTracker.PLog("POST Data %s", jsonPayload)

            if (isDebug) {
                ParselyTracker.PLog("Debug mode on. Not sending to Parse.ly")
                localStorageRepository.purgeStoredQueue()
            } else {
                ParselyTracker.PLog("Requested %s", ParselyTracker.ROOT_URL)
                parselyAPIConnection.send(jsonPayload)
                    .fold(
                        onSuccess = {
                            ParselyTracker.PLog("Pixel request success")
                            parselyTracker.purgeEventsQueue()
                            ParselyTracker.PLog("Event queue empty, flush timer cleared.")
                            parselyTracker.stopFlushTimer()
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
