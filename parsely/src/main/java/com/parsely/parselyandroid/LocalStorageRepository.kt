package com.parsely.parselyandroid

import android.content.Context
import java.io.EOFException
import java.io.FileNotFoundException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

internal open class LocalStorageRepository(private val context: Context) {
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
        } catch (ex: Exception) {
            ParselyTracker.PLog("Exception thrown during queue serialization: %s", ex.toString())
        }
    }

    /**
     * Delete the stored queue from persistent storage.
     */
    fun purgeStoredQueue() {
        persistObject(ArrayList<Map<String, Any>>())
    }

    open val storedQueue: ArrayList<Map<String, Any?>?>
        /**
         * Get the stored event queue from persistent storage.
         *
         * @return The stored queue of events.
         */
        get() {
            var storedQueue: ArrayList<Map<String, Any?>?>? = null
            try {
                val fis = context.applicationContext.openFileInput(STORAGE_KEY)
                val ois = ObjectInputStream(fis)
                storedQueue = ois.readObject() as ArrayList<Map<String, Any?>?>
                ois.close()
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
            if (storedQueue == null) {
                storedQueue = ArrayList()
            }
            return storedQueue
        }

    /**
     * Delete an event from the stored queue.
     */
    open fun expelStoredEvent() {
        val storedQueue = storedQueue
        storedQueue.removeAt(0)
    }

    /**
     * Save the event queue to persistent storage.
     */
    @Synchronized
    open fun persistQueue(inMemoryQueue: List<Map<String, Any?>?>) {
        ParselyTracker.PLog("Persisting event queue")
        val storedQueue = storedQueue
        val hs = HashSet<Map<String, Any?>?>()
        hs.addAll(storedQueue)
        hs.addAll(inMemoryQueue)
        storedQueue.clear()
        storedQueue.addAll(hs)
        persistObject(storedQueue)
    }

    companion object {
        private const val STORAGE_KEY = "parsely-events.ser"
    }
}