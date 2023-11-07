package com.parsely.parselyandroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class QueueManager(
    private val localStorageRepository: LocalStorageRepository,
    private val coroutineScope: CoroutineScope,
    private val onEventAdded: () -> Unit,
) {

    private val mutex = Mutex()

    fun addEvent(event: Map<String, Any?>) {
        coroutineScope.launch {
            mutex.withLock {
                localStorageRepository.persistEvent(event)
                onEventAdded()
            }
        }
    }

    companion object {
        const val STORAGE_SIZE_LIMIT = 100
    }
}
