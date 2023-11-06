package com.parsely.parselyandroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class QueueManager(
    private val parselyTracker: ParselyTracker,
    private val localStorageRepository: LocalStorageRepository,
    private val coroutineScope: CoroutineScope,
) {

    private val mutex = Mutex()

    fun validateQueue() {
        coroutineScope.launch {
            mutex.withLock {
                if (parselyTracker.inMemoryQueue.size > QUEUE_SIZE_LIMIT) {
                    ParselyTracker.PLog("Queue size exceeded, expelling oldest event to persistent memory")
                    val copyInMemoryQueue = parselyTracker.inMemoryQueue.toList()
                    localStorageRepository.persistQueue(copyInMemoryQueue)
                    parselyTracker.inMemoryQueue.removeFirstOrNull()
                    // if persisted storage is too big, expel one
                    if (parselyTracker.storedEventsCount() > STORAGE_SIZE_LIMIT) {
                        localStorageRepository.expelStoredEvent()
                    }
                }
            }
        }
    }

    companion object {
        const val QUEUE_SIZE_LIMIT = 50
        const val STORAGE_SIZE_LIMIT = 100
    }
}
