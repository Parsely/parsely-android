package com.parsely.parselyandroid

import android.content.Context
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface QueueRepository {
    suspend fun remove(toRemove: List<Map<String, Any?>?>)
    suspend fun getStoredQueue(): ArrayList<Map<String, Any?>?>
    suspend fun insertEvents(toInsert: List<Map<String, Any?>?>)
}

internal class LocalStorageRepository(private val context: Context): QueueRepository {

    private val mutex = Mutex()

    /**
     * Persist an object to storage.
     *
     * @param o Object to store.
     */
    private fun persistObject(o: Any) {
        try {
            val fos = context.applicationContext.openFileOutput(
                STORAGE_KEY,
                Context.MODE_PRIVATE
            )
            val oos = ObjectOutputStream(fos)
            oos.writeObject(o)
            oos.close()
            fos.close()
        } catch (ex: Exception) {
            ParselyTracker.PLog("Exception thrown during queue serialization: %s", ex.toString())
        }
    }

    override suspend fun remove(toRemove: List<Map<String, Any?>?>) {
        val storedEvents = getStoredQueue()

        mutex.withLock {
            persistObject(storedEvents - toRemove.toSet())
        }
    }

    /**
     * Get the stored event queue from persistent storage.
     *
     * @return The stored queue of events.
     */
    override suspend fun getStoredQueue(): ArrayList<Map<String, Any?>?> = mutex.withLock {

        var storedQueue: ArrayList<Map<String, Any?>?> = ArrayList()
        try {
            val fis = context.applicationContext.openFileInput(STORAGE_KEY)
            val ois = ObjectInputStream(fis)
            @Suppress("UNCHECKED_CAST")
            storedQueue = ois.readObject() as ArrayList<Map<String, Any?>?>
            ois.close()
            fis.close()
        } catch (ex: EOFException) {
            // Nothing to do here.
        } catch (ex: FileNotFoundException) {
            // Nothing to do here. Means there was no saved queue.
        } catch (ex: Exception) {
            ParselyTracker.PLog(
                "Exception thrown during queue deserialization: %s",
                ex.toString()
            )
        }
        return storedQueue
    }

    /**
     * Save the event queue to persistent storage.
     */
    override suspend fun insertEvents(toInsert: List<Map<String, Any?>?>){
        val storedEvents = getStoredQueue()

        mutex.withLock {
            persistObject(ArrayList((toInsert + storedEvents).distinct()))
        }
    }

    companion object {
        private const val STORAGE_KEY = "parsely-events.ser"
    }
}
