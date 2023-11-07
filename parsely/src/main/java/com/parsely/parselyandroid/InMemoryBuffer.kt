package com.parsely.parselyandroid

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryBuffer(
    private val coroutineScope: CoroutineScope,
    private val localStorageRepository: LocalStorageRepository,
    private val onEventAddedListener: () -> Unit,
) {

    private val mutex = Mutex()
    private val buffer = mutableListOf<Map<String, Any?>>()

    init {
        coroutineScope.launch {
            while (isActive) {
                mutex.withLock {
                    if (buffer.isNotEmpty()) {
                        localStorageRepository.insertEvents(buffer)
                        buffer.clear()
                    }
                }
                delay(1.seconds)
            }
        }
    }

    fun add(event: Map<String, Any>) {
        coroutineScope.launch {
            mutex.withLock {
                ParselyTracker.PLog("Event added")
                buffer.add(event)
                onEventAddedListener()
            }
        }
    }
}
