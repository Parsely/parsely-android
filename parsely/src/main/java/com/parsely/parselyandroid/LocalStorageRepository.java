package com.parsely.parselyandroid;

import static com.parsely.parselyandroid.ParselyTracker.PLog;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

class LocalStorageRepository {
    private static final String STORAGE_KEY = "parsely-events.ser";

    private final Context context;

    LocalStorageRepository(Context context) {
        this.context = context;
    }

    /**
     * Persist an object to storage.
     *
     * @param o Object to store.
     */
    private void persistObject(Object o) {
        try {
            FileOutputStream fos = context.getApplicationContext().openFileOutput(
                    STORAGE_KEY,
                    android.content.Context.MODE_PRIVATE
            );
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(o);
            oos.close();
        } catch (Exception ex) {
            PLog("Exception thrown during queue serialization: %s", ex.toString());
        }
    }

    /**
     * Delete the stored queue from persistent storage.
     */
    void purgeStoredQueue() {
        persistObject(new ArrayList<Map<String, Object>>());
    }

    /**
     * Get the stored event queue from persistent storage.
     *
     * @return The stored queue of events.
     */
    @NonNull
    ArrayList<Map<String, Object>> getStoredQueue() {
        ArrayList<Map<String, Object>> storedQueue = null;
        try {
            FileInputStream fis = context.getApplicationContext().openFileInput(STORAGE_KEY);
            ObjectInputStream ois = new ObjectInputStream(fis);
            //noinspection unchecked
            storedQueue = (ArrayList<Map<String, Object>>) ois.readObject();
            ois.close();
        } catch (EOFException ex) {
            // Nothing to do here.
        } catch (FileNotFoundException ex) {
            // Nothing to do here. Means there was no saved queue.
        } catch (Exception ex) {
            PLog("Exception thrown during queue deserialization: %s", ex.toString());
        }

        if (storedQueue == null) {
            storedQueue = new ArrayList<>();
        }
        return storedQueue;
    }

    /**
     * Delete an event from the stored queue.
     */
    void expelStoredEvent() {
        ArrayList<Map<String, Object>> storedQueue = getStoredQueue();
        storedQueue.remove(0);
    }

    /**
     * Save the event queue to persistent storage.
     */
    synchronized void persistQueue(@NonNull final List<Map<String, Object>> inMemoryQueue) {
        PLog("Persisting event queue");
        ArrayList<Map<String, Object>> storedQueue = getStoredQueue();
        HashSet<Map<String, Object>> hs = new HashSet<>();
        hs.addAll(storedQueue);
        hs.addAll(inMemoryQueue);
        storedQueue.clear();
        storedQueue.addAll(hs);
        persistObject(storedQueue);
    }
}
