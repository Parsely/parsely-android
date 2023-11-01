/*
    Copyright 2016 Parse.ly, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.parsely.parselyandroid;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Timer;
import java.util.UUID;

/**
 * Tracks Parse.ly app views in Android apps
 * <p>
 * Accessed as a singleton. Maintains a queue of pageview events in memory and periodically
 * flushes the queue to the Parse.ly pixel proxy server.
 */
public class ParselyTracker {
    private static ParselyTracker instance = null;
    private static final int DEFAULT_FLUSH_INTERVAL_SECS = 60;
    private static final int DEFAULT_ENGAGEMENT_INTERVAL_MILLIS = 10500;
    private static final int QUEUE_SIZE_LIMIT = 50;
    private static final int STORAGE_SIZE_LIMIT = 100;
    private static final String STORAGE_KEY = "parsely-events.ser";
    @SuppressWarnings("StringOperationCanBeSimplified")
//    private static final String ROOT_URL = "http://10.0.2.2:5001/".intern(); // emulator localhost
    private static final String ROOT_URL = "https://p1.parsely.com/".intern();
    protected ArrayList<Map<String, Object>> eventQueue;
    private boolean isDebug;
    private final Context context;
    private final Timer timer;
    private final FlushManager flushManager;
    private EngagementManager engagementManager, videoEngagementManager;
    @Nullable
    private String lastPageviewUuid = null;
    @NonNull
    private final EventsBuilder eventsBuilder;
    @NonNull final HeartbeatIntervalCalculator intervalCalculator = new HeartbeatIntervalCalculator(new Clock());

    /**
     * Create a new ParselyTracker instance.
     */
    protected ParselyTracker(String siteId, int flushInterval, Context c) {
        context = c.getApplicationContext();
        eventsBuilder = new EventsBuilder(context, siteId);

        // get the adkey straight away on instantiation
        timer = new Timer();
        isDebug = false;

        eventQueue = new ArrayList<>();

        flushManager = new FlushManager(this, flushInterval * 1000L,
                ParselyCoroutineScopeKt.getSdkScope());

        if (getStoredQueue().size() > 0) {
            startFlushTimer();
        }

        ProcessLifecycleOwner.get().getLifecycle().addObserver(
                (LifecycleEventObserver) (lifecycleOwner, event) -> {
                    if (event == Lifecycle.Event.ON_STOP) {
                        PLog("App is in ON_STOP. Flushing events.");
                        flushEvents();
                    }
                }
        );
    }

    /**
     * Singleton instance accessor. Note: This must be called after {@link #sharedInstance(String, Context)}
     *
     * @return The singleton instance
     */
    public static ParselyTracker sharedInstance() {
        if (instance == null) {
            return null;
        }
        return instance;
    }

    /**
     * Singleton instance factory Note: this must be called before {@link #sharedInstance()}
     *
     * @param siteId The Parsely public site id (eg "example.com")
     * @param c      The current Android application context
     * @return The singleton instance
     */
    public static ParselyTracker sharedInstance(String siteId, Context c) {
        return ParselyTracker.sharedInstance(siteId, DEFAULT_FLUSH_INTERVAL_SECS, c);
    }

    /**
     * Singleton instance factory Note: this must be called before {@link #sharedInstance()}
     *
     * @param siteId        The Parsely public site id (eg "example.com")
     * @param flushInterval The interval at which the events queue should flush, in seconds
     * @param c             The current Android application context
     * @return The singleton instance
     */
    public static ParselyTracker sharedInstance(String siteId, int flushInterval, Context c) {
        if (instance == null) {
            instance = new ParselyTracker(siteId, flushInterval, c);
        }
        return instance;
    }

    /**
     * Log a message to the console.
     */
    static void PLog(String logString, Object... objects) {
        if (logString.equals("")) {
            return;
        }
        System.out.println(new Formatter().format("[Parsely] " + logString, objects).toString());
    }

    /**
     * Get the heartbeat interval
     *
     * @return The base engagement tracking interval.
     */
    public double getEngagementInterval() {
        if (engagementManager == null) {
            return -1;
        }
        return engagementManager.getIntervalMillis();
    }

    public double getVideoEngagementInterval() {
        if (videoEngagementManager == null) {
            return -1;
        }
        return videoEngagementManager.getIntervalMillis();
    }

    /**
     * Returns whether the engagement tracker is running.
     *
     * @return Whether the engagement tracker is running.
     */
    public boolean engagementIsActive() {
        return engagementManager != null && engagementManager.isRunning();
    }

    /**
     * Returns whether video tracking is active.
     *
     * @return Whether video tracking is active.
     */
    public boolean videoIsActive() {
        return videoEngagementManager != null && videoEngagementManager.isRunning();
    }

    /**
     * Returns the interval at which the event queue is flushed to Parse.ly.
     *
     * @return The interval at which the event queue is flushed to Parse.ly.
     */
    public long getFlushInterval() {
        return flushManager.getIntervalMillis() / 1000;
    }

    /**
     * Getter for isDebug
     *
     * @return Whether debug mode is active.
     */
    public boolean getDebug() {
        return isDebug;
    }

    /**
     * Set a debug flag which will prevent data from being sent to Parse.ly
     * <p>
     * Use this flag when developing to prevent the SDK from actually sending requests
     * to Parse.ly servers. The value it would otherwise send is logged to the console.
     *
     * @param debug Value to use for debug flag.
     */
    public void setDebug(boolean debug) {
        isDebug = debug;
        PLog("Debugging is now set to " + isDebug);
    }

    /**
     * Register a pageview event using a URL and optional metadata.
     *
     * @param url         The URL of the article being tracked
     *                    (eg: "http://example.com/some-old/article.html")
     * @param urlRef      Referrer URL associated with this video view.
     * @param urlMetadata Optional metadata for the URL -- not used in most cases. Only needed
     *                    when `url` isn't accessible over the Internet (i.e. app-only
     *                    content). Do not use this for **content also hosted on** URLs Parse.ly
     *                    would normally crawl.
     * @param extraData   A Map of additional information to send with the event.
     */
    public void trackPageview(
            @NonNull String url,
            @Nullable String urlRef,
            @Nullable ParselyMetadata urlMetadata,
            @Nullable Map<String, Object> extraData) {
        if (url.equals("")) {
            throw new IllegalArgumentException("url cannot be null or empty.");
        }

        // Blank urlref is better than null
        if (urlRef == null) {
            urlRef = "";
        }

        lastPageviewUuid = generatePixelId();

        enqueueEvent(eventsBuilder.buildEvent(url, urlRef, "pageview", urlMetadata, extraData, lastPageviewUuid));
    }

    /**
     * Start engaged time tracking for the given URL.
     * <p>
     * This starts a timer which will send events to Parse.ly on a regular basis
     * to capture engaged time for this URL. The value of `url` should be a URL for
     * which `trackPageview` has been called.
     *
     * @param url    The URL to track engaged time for.
     * @param urlRef Referrer URL associated with this video view.
     */
    public void startEngagement(@NonNull String url, @Nullable String urlRef) {
        startEngagement(url, urlRef, null);
    }

    /**
     * Same as {@link #startEngagement(String, String)} but with extra data.
     *
     * @param url       The URL to track engaged time for.
     * @param urlRef    Referrer URL associated with this video view.
     * @param extraData A Map of additional information to send with the event.
     */
    public void startEngagement(
            final @NonNull String url,
            @Nullable String urlRef,
            final @Nullable Map<String, Object> extraData
    ) {
        if (url.equals("")) {
            throw new IllegalArgumentException("url cannot be null or empty.");
        }

        // Blank urlref is better than null
        if (urlRef == null) {
            urlRef = "";
        }
        // Cancel anything running
        stopEngagement();

        // Start a new EngagementTask
        Map<String, Object> event = eventsBuilder.buildEvent(url, urlRef, "heartbeat", null, extraData, lastPageviewUuid);
        engagementManager = new EngagementManager(this, timer, DEFAULT_ENGAGEMENT_INTERVAL_MILLIS, event, intervalCalculator);
        engagementManager.start();
    }

    /**
     * Stop engaged time tracking.
     * <p>
     * Stops the engaged time tracker, sending any accumulated engaged time to Parse.ly.
     * NOTE: This **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    public void stopEngagement() {
        if (engagementManager == null) {
            return;
        }
        engagementManager.stop();
        engagementManager = null;
    }

    /**
     * Start video tracking.
     * <p>
     * Starts tracking view time for a video being viewed at a given url. Will send a `videostart`
     * event unless the same url/videoId had previously been paused.
     * Video metadata must be provided, specifically the video ID and video duration.
     * <p>
     * The `url` value is *not* the URL of a video, but the post which contains the video. If the video
     * is not embedded in a post, then this should contain a well-formatted URL on the customer's
     * domain (e.g. http://<CUSTOMERDOMAIN>/app-videos). This URL doesn't need to return a 200 status
     * when crawled, but must but well-formatted so Parse.ly systems recognize it as belonging to
     * the customer.
     *
     * @param url           URL of post the video is embedded in. If videos is not embedded, a
     *                      valid URL for the customer should still be provided.
     *                      (e.g. http://<CUSTOMERDOMAIN>/app-videos)
     * @param urlRef        Referrer URL associated with this video view.
     * @param videoMetadata Metadata about the video being tracked.
     * @param extraData     A Map of additional information to send with the event.
     */
    public void trackPlay(
            @NonNull String url,
            @Nullable String urlRef,
            @NonNull ParselyVideoMetadata videoMetadata,
            @Nullable Map<String, Object> extraData) {
        if (url.equals("")) {
            throw new IllegalArgumentException("url cannot be null or empty.");
        }

        // Blank urlref is better than null
        if (urlRef == null) {
            urlRef = "";
        }

        // If there is already an engagement manager for this video make sure it is started.
        if (videoEngagementManager != null) {
            if (videoEngagementManager.isSameVideo(url, urlRef, videoMetadata)) {
                if (!videoEngagementManager.isRunning()) {
                    videoEngagementManager.start();
                }
                return; // all done here. early exit.
            } else {
                // Different video. Stop and remove it so we can start fresh.
                videoEngagementManager.stop();
                videoEngagementManager = null;
            }
        }
        @NonNull final String uuid = generatePixelId();

        // Enqueue the videostart
        @NonNull final Map<String, Object> videostartEvent = eventsBuilder.buildEvent(url, urlRef, "videostart", videoMetadata, extraData, uuid);
        enqueueEvent(videostartEvent);

        // Start a new engagement manager for the video.
        @NonNull final Map<String, Object> hbEvent = eventsBuilder.buildEvent(url, urlRef, "vheartbeat", videoMetadata, extraData, uuid);
        // TODO: Can we remove some metadata fields from this request?
        videoEngagementManager = new EngagementManager(this, timer, DEFAULT_ENGAGEMENT_INTERVAL_MILLIS, hbEvent, intervalCalculator);
        videoEngagementManager.start();
    }

    /**
     * Pause video tracking.
     * <p>
     * Pauses video tracking for an ongoing video. If {@link #trackPlay} is immediately called again for
     * the same video, a new video start event will not be sent. This models a user pausing a
     * playing video.
     * <p>
     * NOTE: This or {@link #resetVideo} **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    public void trackPause() {
        if (videoEngagementManager == null) {
            return;
        }
        videoEngagementManager.stop();
    }

    /**
     * Reset tracking on a video.
     * <p>
     * Stops video tracking and resets internal state for the video. If {@link #trackPlay} is immediately
     * called for the same video, a new video start event is set. This models a user stopping a
     * video and (on {@link #trackPlay} being called again) starting it over.
     * <p>
     * NOTE: This or {@link #trackPause} **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    public void resetVideo() {
        if (videoEngagementManager == null) {
            return;
        }
        videoEngagementManager.stop();
        videoEngagementManager = null;
    }

    /**
     * Add an event Map to the queue.
     * <p>
     * Place a data structure representing the event into the in-memory queue for later use.
     * <p>
     * **Note**: Events placed into this queue will be discarded if the size of the persistent queue
     * store exceeds {@link #STORAGE_SIZE_LIMIT}.
     *
     * @param event The event Map to enqueue.
     */
    void enqueueEvent(Map<String, Object> event) {
        // Push it onto the queue
        eventQueue.add(event);
        new QueueManager().execute();
        if (!flushTimerIsActive()) {
            startFlushTimer();
            PLog("Flush flushTimer set to %ds", (flushManager.getIntervalMillis() / 1000));
        }
    }

    /**
     * Deprecated since 3.1.1. The SDK now automatically flushes the queue on app lifecycle events.
     * Any usage of this method is safe to remove and will have no effect. Keeping for backwards compatibility.
     */
    @Deprecated
    public void flushEventQueue() {
        // no-op
    }

    /**
     * Send the batched event request to Parsely.
     * <p>
     * Creates a POST request containing the JSON encoding of the event queue.
     * Sends this request to Parse.ly servers.
     *
     * @param events The list of event dictionaries to serialize
     */
    private void sendBatchRequest(ArrayList<Map<String, Object>> events) {
        if (events == null || events.size() == 0) {
            return;
        }
        PLog("Sending request with %d events", events.size());

        // Put in a Map for the proxy server
        Map<String, Object> batchMap = new HashMap<>();
        batchMap.put("events", events);

        if (isDebug) {
            PLog("Debug mode on. Not sending to Parse.ly");
            purgeEventsQueue();
        } else {
            new ParselyAPIConnection(this).execute(ROOT_URL + "mobileproxy", JsonEncode(batchMap));
            PLog("Requested %s", ROOT_URL);
        }
        PLog("POST Data %s", JsonEncode(batchMap));
    }

    /**
     * Returns whether the network is accessible and Parsely is reachable.
     *
     * @return Whether the network is accessible and Parsely is reachable.
     */
    private boolean isReachable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    /**
     * Save the event queue to persistent storage.
     */
    private synchronized void persistQueue() {
        PLog("Persisting event queue");
        ArrayList<Map<String, Object>> storedQueue = getStoredQueue();
        HashSet<Map<String, Object>> hs = new HashSet<>();
        hs.addAll(storedQueue);
        hs.addAll(eventQueue);
        storedQueue.clear();
        storedQueue.addAll(hs);
        persistObject(storedQueue);
    }

    /**
     * Get the stored event queue from persistent storage.
     *
     * @return The stored queue of events.
     */
    @NonNull
    private ArrayList<Map<String, Object>> getStoredQueue() {
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

    void purgeEventsQueue() {
        eventQueue.clear();
        purgeStoredQueue();
    }

    /**
     * Delete the stored queue from persistent storage.
     */
    private void purgeStoredQueue() {
        persistObject(new ArrayList<Map<String, Object>>());
    }

    /**
     * Delete an event from the stored queue.
     */
    private void expelStoredEvent() {
        ArrayList<Map<String, Object>> storedQueue = getStoredQueue();
        storedQueue.remove(0);
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
     * Encode an event Map as JSON.
     *
     * @param map The Map object to encode as JSON.
     * @return The JSON-encoded value of `map`.
     */
    private String JsonEncode(Map<String, Object> map) {
        ObjectMapper mapper = new ObjectMapper();
        String ret = null;
        try {
            StringWriter strWriter = new StringWriter();
            mapper.writeValue(strWriter, map);
            ret = strWriter.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    /**
     * Start the timer to flush events to Parsely.
     * <p>
     * Instantiates the callback timer responsible for flushing the events queue.
     * Can be called before of after `stop`, but has no effect if used before instantiating the
     * singleton
     */
    public void startFlushTimer() {
        flushManager.start();
    }

    /**
     * Returns whether the event queue flush timer is running.
     *
     * @return Whether the event queue flush timer is running.
     */
    public boolean flushTimerIsActive() {
        return flushManager.isRunning();
    }

    /**
     * Stop the event queue flush timer.
     */
    public void stopFlushTimer() {
        flushManager.stop();
    }

    @NonNull
    private String generatePixelId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Get the number of events waiting to be flushed to Parsely.
     *
     * @return The number of events waiting to be flushed to Parsely.
     */
    public int queueSize() {
        return eventQueue.size();
    }

    /**
     * Get the number of events stored in persistent storage.
     *
     * @return The number of events stored in persistent storage.
     */
    public int storedEventsCount() {
        ArrayList<Map<String, Object>> ar = getStoredQueue();
        return ar.size();
    }

    private class QueueManager extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            // if event queue is too big, push to persisted storage
            if (eventQueue.size() > QUEUE_SIZE_LIMIT) {
                PLog("Queue size exceeded, expelling oldest event to persistent memory");
                persistQueue();
                eventQueue.remove(0);
                // if persisted storage is too big, expel one
                if (storedEventsCount() > STORAGE_SIZE_LIMIT) {
                    expelStoredEvent();
                }
            }
            return null;
        }
    }

    private class FlushQueue extends AsyncTask<Void, Void, Void> {
        @Override
        protected synchronized Void doInBackground(Void... params) {
            ArrayList<Map<String, Object>> storedQueue = getStoredQueue();
            PLog("%d events in queue, %d stored events", eventQueue.size(), storedEventsCount());
            // in case both queues have been flushed and app quits, don't crash
            if ((eventQueue == null || eventQueue.size() == 0) && storedQueue.size() == 0) {
                stopFlushTimer();
                return null;
            }
            if (!isReachable()) {
                PLog("Network unreachable. Not flushing.");
                return null;
            }
            HashSet<Map<String, Object>> hs = new HashSet<>();
            ArrayList<Map<String, Object>> newQueue = new ArrayList<>();

            hs.addAll(eventQueue);
            hs.addAll(storedQueue);
            newQueue.addAll(hs);
            PLog("Flushing queue");
            sendBatchRequest(newQueue);
            return null;
        }
    }

    void flushEvents() {
        new FlushQueue().execute();
    }

}
