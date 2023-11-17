package com.parsely.parselyandroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun initialize(
    scope: CoroutineScope,
    localStorageRepository: LocalStorageRepository,
    flushManager: FlushManager,
) {
    scope.launch {
        if (localStorageRepository.getStoredQueue().isNotEmpty()) {
            flushManager.start()
        }
    }
}
