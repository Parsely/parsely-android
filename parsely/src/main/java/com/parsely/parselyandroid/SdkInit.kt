package com.parsely.parselyandroid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SdkInit(
    private val scope: CoroutineScope,
    private val localStorageRepository: LocalStorageRepository,
    private val flushManager: FlushManager,
) {
    fun initialize() {
        scope.launch {
            if (localStorageRepository.getStoredQueue().isNotEmpty()) {
                flushManager.start()
            }
        }
    }
}
