package com.parsely.parselyandroid

import com.parsely.parselyandroid.JsonSerializer.toJson

internal class SendEvents(
    private val parselyTracker: ParselyTracker,
    private val localStorageRepository: LocalStorageRepository
) {

    operator fun invoke(isDebug: Boolean) {
        val eventsToSend = localStorageRepository.getStoredQueue()

        if (eventsToSend.isEmpty()) {
            return
        }
        ParselyTracker.PLog("Sending request with %d events", eventsToSend.size)

        val batchMap: MutableMap<String, Any> = HashMap()
        batchMap["events"] = eventsToSend
        val jsonPayload = toJson(batchMap)

        if (isDebug) {
            ParselyTracker.PLog("Debug mode on. Not sending to Parse.ly")
            localStorageRepository.purgeStoredQueue()
        } else {
            ParselyTracker.PLog("Requested %s", ParselyTracker.ROOT_URL)
            ParselyAPIConnection(parselyTracker).execute(
                ParselyTracker.ROOT_URL + "mobileproxy",
                jsonPayload
            )
        }
        ParselyTracker.PLog("POST Data %s", toJson(batchMap))
    }
}
