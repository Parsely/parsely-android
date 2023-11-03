package com.parsely.parselyandroid

import android.os.AsyncTask

internal class QueueManager(
    private val parselyTracker: ParselyTracker,
    private val localStorageRepository: LocalStorageRepository
) : AsyncTask<Void?, Void?, Void?>() {

    override fun doInBackground(vararg params: Void?): Void? {
        // if event queue is too big, push to persisted storage
        if (parselyTracker.inMemoryQueue.size > QUEUE_SIZE_LIMIT) {
            ParselyTracker.PLog("Queue size exceeded, expelling oldest event to persistent memory")
            localStorageRepository.persistQueue(parselyTracker.inMemoryQueue)
            parselyTracker.inMemoryQueue.removeAt(0)
            // if persisted storage is too big, expel one
            if (parselyTracker.storedEventsCount() > STORAGE_SIZE_LIMIT) {
                localStorageRepository.expelStoredEvent()
            }
        }
        return null
    }

    companion object {
        const val QUEUE_SIZE_LIMIT = 50
        const val STORAGE_SIZE_LIMIT = 100
    }
}
