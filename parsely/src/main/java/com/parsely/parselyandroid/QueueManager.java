package com.parsely.parselyandroid;

import android.os.AsyncTask;

import androidx.annotation.NonNull;

class QueueManager extends AsyncTask<Void, Void, Void> {
    private static final int QUEUE_SIZE_LIMIT = 50;
    private static final int STORAGE_SIZE_LIMIT = 100;

    @NonNull
    private final ParselyTracker parselyTracker;
    @NonNull
    private final LocalStorageRepository localStorageRepository;

    public QueueManager(
            @NonNull ParselyTracker parselyTracker,
            @NonNull LocalStorageRepository localStorageRepository
    ) {
        this.parselyTracker = parselyTracker;
        this.localStorageRepository = localStorageRepository;
    }

    @Override
    protected Void doInBackground(Void... params) {
        // if event queue is too big, push to persisted storage
        if (parselyTracker.eventQueue.size() > QUEUE_SIZE_LIMIT) {
            ParselyTracker.PLog("Queue size exceeded, expelling oldest event to persistent memory");
            localStorageRepository.persistQueue(parselyTracker.eventQueue);
            parselyTracker.eventQueue.remove(0);
            // if persisted storage is too big, expel one
            if (parselyTracker.storedEventsCount() > STORAGE_SIZE_LIMIT) {
                localStorageRepository.expelStoredEvent();
            }
        }
        return null;
    }
}
