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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings.Secure;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/*! \brief Tracks Parse.ly app views in Android apps
 *
 *  Accessed as a singleton. Maintains a queue of pageview events in memory and periodically
 *  flushes the queue to the Parse.ly pixel proxy server.
 */
public class ParselyTracker {
    private static ParselyTracker instance = null;
    private static int DEFAULT_FLUSH_INTERVAL_SECS = 60;
    private static int DEFAULT_ENGAGEMENT_INTERVAL_MILLIS = 10500;
    protected ArrayList<Map<String, Object>> eventQueue;
    private String siteId, rootUrl, storageKey, uuidKey, adKey;
    private boolean isDebug;
    private SharedPreferences settings;
    private int queueSizeLimit, storageSizeLimit;
    private Map<String, String> deviceInfo;
    private Context context;
    private Timer timer;
    private FlushManager flushManager;
    private EngagementManager engagementManager, videoEngagementManager;

    /*! \brief Create a new ParselyTracker instance.
     *
     */
    protected ParselyTracker(String siteId, int flushInterval, Context c) {
        this.context = c.getApplicationContext();
        this.settings = this.context.getSharedPreferences("parsely-prefs", 0);

        this.siteId = siteId;
        this.uuidKey = "parsely-uuid";
        this.adKey = null;
        // get the adkey straight away on instantiation
        new GetAdKey(c).execute();
        this.storageKey = "parsely-events.ser";
        //this.rootUrl = "http://10.0.2.2:5001/";  // emulator localhost
        this.rootUrl = "https://p1.parsely.com/";
        this.queueSizeLimit = 50;
        this.storageSizeLimit = 100;
        this.deviceInfo = this.collectDeviceInfo();
        this.timer = new Timer();
        this.isDebug = false;

        this.eventQueue = new ArrayList<>();

        this.flushManager = new FlushManager(this.timer, flushInterval * 1000);

        if (this.getStoredQueue() != null && this.getStoredQueue().size() > 0) {
            this.startFlushTimer();
        }
    }

    /*! \brief Singleton instance accessor. Note: This must be called after
    sharedInstance(String, Context)
    *
    *  @return The singleton instance
    */
    public static ParselyTracker sharedInstance() {
        if (instance == null) {
            return null;
        }
        return instance;
    }

    /*! \brief Singleton instance factory Note: this must be called before `sharedInstance()`
     *
     *  @param siteId The Parsely public site id (eg "example.com")
     *  @param c      The current Android application context
     *  @return       The singleton instance
     */
    public static ParselyTracker sharedInstance(String siteId, Context c) {
        return ParselyTracker.sharedInstance(siteId, DEFAULT_FLUSH_INTERVAL_SECS, c);
    }

    /*! \brief Singleton instance factory Note: this must be called before `sharedInstance()`
     *
     *  @param siteId        The Parsely public site id (eg "example.com")
     *  @param flushInterval The interval at which the events queue should flush, in seconds
     *  @param c             The current Android application context
     *  @return              The singleton instance
     */
    public static ParselyTracker sharedInstance(String siteId, int flushInterval, Context c) {
        if (instance == null) {
            instance = new ParselyTracker(siteId, flushInterval, c);
        }
        return instance;
    }

    /*! \brief Log a message to the console.
     *
     */
    protected static void PLog(String logString, Object... objects) {
        if (logString.equals("")) {
            return;
        }
        System.out.println(new Formatter().format("[Parsely] " + logString, objects).toString());
    }

    /*! \brief Get the heartbeat interval
     *
     * @return The base engagement tracking interval.
     */
    public double getEngagementInterval() {
        if (this.engagementManager == null) {
            return -1;
        }
        return this.engagementManager.getIntervalMillis();
    }

    public double getVideoEngagementInterval() {
        if (this.videoEngagementManager == null) {
            return -1;
        }
        return this.videoEngagementManager.getIntervalMillis();
    }

    /*! \brief Returns whether the engagement tracker is running.
     *
     * @return Whether the engagement tracker is running.
     */
    public boolean engagementIsActive() {
        return this.engagementManager != null && this.engagementManager.started;
    }

    /*! \brief Returns whether video tracking is active.
     *
     * @return Whether video tracking is active.
     */
    public boolean videoIsActive() {
        return this.videoEngagementManager != null && this.videoEngagementManager.started;
    }

    /*! \brief Returns the interval at which the event queue is flushed to Parse.ly.
     *
     * @return The interval at which the event queue is flushed to Parse.ly.
     */
    public long getFlushInterval() {
        return this.flushManager.getIntervalMillis() / 1000;
    }

    /*! \brief Getter for this.isDebug
     *
     * @return Whether debug mode is active.
     */
    public boolean getDebug() {
        return isDebug;
    }

    /*! \brief Set a debug flag which will prevent data from being sent to Parse.ly
     *
     *  Use this flag when developing to prevent the SDK from actually sending requests
     *  to Parse.ly servers. The value it would otherwise send is logged to the console.
     *
     *  @param debug Value to use for debug flag.
     */
    public void setDebug(boolean debug) {
        isDebug = debug;
        PLog("Debugging is now set to " + isDebug);
    }

    /*! \brief Register a pageview event using a URL and optional metadata.
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
        if (url == null || url.equals("")) {
            throw new NullPointerException("url cannot be null or empty.");
        }

        // Blank urlref is better than null
        if (urlRef == null) {
            urlRef = "";
        }
        this.enqueueEvent(this.buildEvent(url, urlRef, "pageview", urlMetadata, extraData));
    }

    /*! \brief Start engaged time tracking for the given URL.
     *
     * This starts a timer which will send events to Parse.ly on a regular basis
     * to capture engaged time for this URL. The value of `url` should be a URL for
     * which `trackPageview` has been called.
     *
     * @param url    The URL to track engaged time for.
     * @param urlRef Referrer URL associated with this video view.
     */
    public void startEngagement(@NonNull String url, @Nullable String urlRef) {
        if (url == null || url.equals("")) {
            throw new NullPointerException("url cannot be null or empty.");
        }

        // Blank urlref is better than null
        if (urlRef == null) {
            urlRef = "";
        }
        // Cancel anything running
        this.stopEngagement();

        // Start a new EngagementTask
        Map<String, Object> event = this.buildEvent(url, urlRef, "heartbeat", null, null);
        this.engagementManager = new EngagementManager(this.timer, DEFAULT_ENGAGEMENT_INTERVAL_MILLIS, event);
        this.engagementManager.start();
    }

    /*! \brief Stop engaged time tracking.
     *
     * Stops the engaged time tracker, sending any accumulated engaged time to Parse.ly.
     * NOTE: This **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    public void stopEngagement() {
        if (this.engagementManager == null) {
            return;
        }
        this.engagementManager.stop();
        this.engagementManager = null;
    }

    /*! \brief Start video tracking.
     *
     * Starts tracking view time for a video being viewed at a given url. Will send a `videostart`
     * event unless the same url/videoId had previously been paused.
     * Video metadata must be provided, specifically the video ID and video duration.
     *
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
        if (videoMetadata == null) {
            throw new NullPointerException("videoMetadata cannot be null.");
        }
        if (url == null || url.equals("")) {
            throw new NullPointerException("url cannot be null or empty.");
        }

        // Blank urlref is better than null
        if (urlRef == null) {
            urlRef = "";
        }

        // If there is already an engagement manager for this video make sure it is started.
        if (this.videoEngagementManager != null) {
            if (this.videoEngagementManager.isSameVideo(url, urlRef, videoMetadata)) {
                if (!this.videoEngagementManager.isRunning()) {
                    this.videoEngagementManager.start();
                }
                return; // all done here. early exit.
            } else {
                // Different video. Stop and remove it so we can start fresh.
                this.videoEngagementManager.stop();
                this.videoEngagementManager = null;
            }
        }

        // Enqueue the videostart
        this.enqueueEvent(this.buildEvent(url, urlRef, "videostart", videoMetadata, extraData));

        // Start a new engagement manager for the video.
        Map<String, Object> hbEvent = this.buildEvent(url, urlRef, "vheartbeat", videoMetadata, extraData);
        // TODO: Can we remove some metadata fields from this request?
        this.videoEngagementManager = new EngagementManager(this.timer, DEFAULT_ENGAGEMENT_INTERVAL_MILLIS, hbEvent);
        this.videoEngagementManager.start();
    }

    /*! \brief Pause video tracking.
     *
     * Pauses video tracking for an ongoing video. If `trackPlay` is immediately called again for
     * the same video, a new video start event will not be sent. This models a user pausing a
     * playing video.
     *
     * NOTE: This or `resetVideo` **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    public void trackPause() {
        if (this.videoEngagementManager == null) {
            return;
        }
        this.videoEngagementManager.stop();
    }

    /*! \brief Reset tracking on a video.
     *
     * Stops video tracking and resets internal state for the video. If `trackPlay` is immediately
     * called for the same video, a new video start event is set. This models a user stopping a
     * video and (on `trackPlay` being called again) starting it over.
     *
     * NOTE: This or `trackPause` **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    public void resetVideo() {
        if (this.videoEngagementManager == null) {
            return;
        }
        this.videoEngagementManager.stop();
        this.videoEngagementManager = null;
    }

    /*! \brief Create an event Map
     *
     *  @param url       The URL identifying the pageview/heartbeat
     *  @param action    Action to use (e.g. pageview, heartbeat, videostart, vheartbeat)
     *  @param metadata  Metadata to attach to the event.
     *  @param extraData A Map of additional information to send with the event.
     *  @return          A Map object representing the event to be sent to Parse.ly.
     */
    private Map<String, Object> buildEvent(
            String url,
            String urlRef,
            String action,
            ParselyMetadata metadata,
            Map<String, Object> extraData) {
        PLog("buildEvent called for %s/%s", action, url);

        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        // Main event info
        Map<String, Object> event = new HashMap<>();
        event.put("url", url);
        event.put("urlref", urlRef);
        event.put("idsite", this.siteId);
        event.put("action", action);

        // Make a copy of extraData and add some things.
        Map<String, Object> data = new HashMap<>();
        if (extraData != null) {
            data.putAll(extraData);
        }
        data.put("manufacturer", this.deviceInfo.get("manufacturer"));
        data.put("os", this.deviceInfo.get("os"));
        data.put("os_version", this.deviceInfo.get("os_version"));
        data.put("ts", now.getTimeInMillis() / 1000);
        data.put("parsely_site_uuid", this.deviceInfo.get("parsely_site_uuid"));
        event.put("data", data);

        if (metadata != null) {
            event.put("metadata", metadata.toMap());
        }

        return event;
    }

    /*! \brief Add an event Map to the queue.
     *
     *  Place a data structure representing the event into the in-memory queue for later use.
     *
     *  **Note**: Events placed into this queue will be discarded if the size of the persistent queue
     *  store exceeds `storageSizeLimit`.
     *
     *  @param event The event Map to enqueue.
     */
    private void enqueueEvent(Map<String, Object> event) {
        // Push it onto the queue
        this.eventQueue.add(event);
        new QueueManager().execute();
        if (this.flushTimerIsActive() == false) {
            this.startFlushTimer();
            PLog("Flush flushTimer set to %ds", (this.flushManager.getIntervalMillis() / 1000));
        }
    }

    /*!  \brief Flush events to Parsely.
     *
     *  Empties the event queue and sends the appropriate requests to Parsely.
     *  Called automatically after a number of seconds determined by `flushInterval`.
     *
     *  To make sure all of the queued events are flushed to Parse.ly's servers,
     * include a call to `flushEventQueue` in your main activity's `onDestroy()` method.
     */
    public void flushEventQueue() {
        // needed for call from MainActivity
        new FlushQueue().execute();
    }

    /*!  \brief Send the batched event request to Parsely.
     *
     *   Creates a POST request containing the JSON encoding of the event queue.
     *   Sends this request to Parse.ly servers.
     *
     *   @param queue The list of event dictionaries to serialize
     */
    private void sendBatchRequest(ArrayList<Map<String, Object>> events) {
        if (events == null || events.size() == 0) {
            return;
        }
        PLog("Sending request with %d events", events.size());

        // Put in a Map for the proxy server
        Map<String, Object> batchMap = new HashMap<>();
        batchMap.put("events", events);

        if (this.isDebug == true) {
            PLog("Debug mode on. Not sending to Parse.ly");
            this.eventQueue.clear();
            this.purgeStoredQueue();
        } else {
            new ParselyAPIConnection().execute(this.rootUrl + "mobileproxy", this.JsonEncode(batchMap));
            PLog("Requested %s", this.rootUrl);
        }
        PLog("POST Data %s", this.JsonEncode(batchMap));
    }

    /*! \brief Returns whether the network is accessible and Parsely is reachable.
     *
     * @return Whether the network is accessible and Parsely is reachable.
     */
    private boolean isReachable() {
        ConnectivityManager cm = (ConnectivityManager) this.context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    /*! \brief Save the event queue to persistent storage.

     */
    private synchronized void persistQueue() {
        PLog("Persisting event queue");
        ArrayList<Map<String, Object>> storedQueue = this.getStoredQueue();
        if (storedQueue == null) {
            storedQueue = new ArrayList<>();
        }
        HashSet<Map<String, Object>> hs = new HashSet<>();
        hs.addAll(storedQueue);
        hs.addAll(this.eventQueue);
        storedQueue.clear();
        storedQueue.addAll(hs);
        this.persistObject(storedQueue);
    }

    /*! \brief Get the stored event queue from persistent storage.
     *
     * @return The stored queue of events.
     */
    private ArrayList<Map<String, Object>> getStoredQueue() {
        ArrayList<Map<String, Object>> storedQueue = null;
        try {
            FileInputStream fis = this.context.getApplicationContext().openFileInput(
                    this.storageKey);
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

    /*! \brief Delete the stored queue from persistent storage.
     *
     */
    protected void purgeStoredQueue() {
        this.persistObject(new ArrayList<Map<String, Object>>());
    }

    /*! \brief Delete an event from the stored queue.
     *
     */
    private void expelStoredEvent() {
        ArrayList<Map<String, Object>> storedQueue = this.getStoredQueue();
        storedQueue.remove(0);
    }

    /*! \brief Persist an object to storage.
     *
     * @param o Object to store.
     */
    private void persistObject(Object o) {
        try {
            FileOutputStream fos = this.context.getApplicationContext().openFileOutput(
                    this.storageKey,
                    android.content.Context.MODE_PRIVATE
            );
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(o);
            oos.close();
        } catch (Exception ex) {
            PLog("Exception thrown during queue serialization: %s", ex.toString());
        }
    }

    /*! \brief Encode an event Map as JSON.
     *
     * @param map The Map object to encode as JSON.
     * @return    The JSON-encoded value of `map`.
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

    /*! \brief Start the timer to flush events to Parsely.
     *
     *  Instantiates the callback timer responsible for flushing the events queue.
     *  Can be called before of after `stop`, but has no effect if used before instantiating the
     *  singleton
     */
    public void startFlushTimer() {
        this.flushManager.start();
    }

    /*! \brief Returns whether the event queue flush timer is running.
     *
     *  @return Whether the event queue flush timer is running.
     */
    public boolean flushTimerIsActive() {
        return this.flushManager.isRunning();
    }

    /*! \brief Stop the event queue flush timer.
     *
     */
    public void stopFlushTimer() {
        this.flushManager.stop();
    }

    /*! \brief Read the Parsely UUID from application context or make a new one.
     *
     * @return The UUID to use for this user.
     */
    private String generateSiteUuid() {
        String uuid = Secure.getString(this.context.getApplicationContext().getContentResolver(),
                Secure.ANDROID_ID);
        PLog(String.format("Generated UUID: %s", uuid));
        return uuid;
    }

    /*! \brief Get the UUID for this user.
     *
     * TODO: docs about where we get this UUID from and how.
     */
    private String getSiteUuid() {
        String uuid = "";
        try {
            uuid = this.settings.getString(this.uuidKey, "");
            if (uuid.equals("")) {
                uuid = this.generateSiteUuid();
            }
        } catch (Exception ex) {
            PLog("Exception caught during site uuid generation: %s", ex.toString());
        }
        return uuid;
    }

    /*! \brief Collect device-specific info.
     *
     * Collects info about the device and user to use in Parsely events.
     */
    private Map<String, String> collectDeviceInfo() {
        Map<String, String> dInfo = new HashMap<>();

        // TODO: screen dimensions (maybe?)
        PLog("adkey is: %s, uuid is %s", this.adKey, this.getSiteUuid());
        String uuid = (this.adKey != null) ? this.adKey : this.getSiteUuid();
        dInfo.put("parsely_site_uuid", uuid);
        dInfo.put("manufacturer", android.os.Build.MANUFACTURER);
        dInfo.put("os", "android");
        dInfo.put("os_version", String.format("%d", android.os.Build.VERSION.SDK_INT));

        // FIXME: Not passed in event or used anywhere else.
        CharSequence txt = this.context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
        dInfo.put("appname", txt.toString());

        return dInfo;
    }

    /*! \brief Get the number of events waiting to be flushed to Parsely.
     *
     * @return The number of events waiting to be flushed to Parsely.
     */
    public int queueSize() {
        return this.eventQueue.size();
    }

    /*! \brief Get the number of events stored in persistent storage.
     *
     * @return The number of events stored in persistent storage.
     */
    public int storedEventsCount() {
        ArrayList<Map<String, Object>> ar = this.getStoredQueue();
        if (ar != null) {
            return ar.size();
        }
        return 0;
    }

    private class QueueManager extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            ArrayList<Map<String, Object>> storedQueue = getStoredQueue();
            // if event queue is too big, push to persisted storage
            if (eventQueue.size() >= queueSizeLimit + 1) {
                PLog("Queue size exceeded, expelling oldest event to persistent memory");
                persistQueue();
                eventQueue.remove(0);
                // if persisted storage is too big, expel one
                if (storedQueue != null) {
                    if (storedEventsCount() > storageSizeLimit) {
                        expelStoredEvent();
                    }
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
            if ((eventQueue == null || eventQueue.size() == 0) &&
                    (storedQueue == null || storedQueue.size() == 0)) {
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
            if (storedQueue != null) {
                hs.addAll(storedQueue);
            }
            newQueue.addAll(hs);
            PLog("Flushing queue");
            sendBatchRequest(newQueue);
            return null;
        }
    }

    /*! \brief Async task to get adKey for this device.
     */
    private class GetAdKey extends AsyncTask<Void, Void, String> {
        private Context mContext;

        public GetAdKey(Context context) {
            mContext = context;
        }

        @Override
        protected String doInBackground(Void... params) {
            AdvertisingIdClient.Info idInfo = null;
            String advertId = null;
            try {
                idInfo = AdvertisingIdClient.getAdvertisingIdInfo(mContext);
            } catch (GooglePlayServicesRepairableException | IOException | GooglePlayServicesNotAvailableException | IllegalArgumentException e) {
                PLog("No Google play services or error! falling back to device uuid");
                // fall back to device uuid on google play errors
                advertId = getSiteUuid();
            }
            try {
                advertId = idInfo.getId();
            } catch (NullPointerException e) {
                advertId = getSiteUuid();
            }
            return advertId;
        }

        @Override
        protected void onPostExecute(String advertId) {
            adKey = advertId;
            deviceInfo.put("parsely_site_uuid", adKey);
        }

    }


    /*! \brief Manager for the event flush timer.
     *
     * Handles stopping and starting the flush timer. The flush timer
     * controls how often we send events to Parse.ly servers.
     */
    private class FlushManager {

        private Timer parentTimer;
        private long intervalMillis;
        private TimerTask runningTask;

        public FlushManager(Timer parentTimer, long intervalMillis) {
            this.parentTimer = parentTimer;
            this.intervalMillis = intervalMillis;
        }

        public void start() {
            if (this.runningTask != null) {
                return;
            }

            this.runningTask = new TimerTask() {
                public void run() {
                    flushEventQueue();
                }
            };
            this.parentTimer.scheduleAtFixedRate(this.runningTask, intervalMillis, intervalMillis);
        }

        public boolean stop() {
            if (this.runningTask == null) {
                return false;
            } else {
                boolean output = this.runningTask.cancel();
                this.runningTask = null;
                return output;
            }
        }

        public boolean isRunning() {
            return this.runningTask != null;
        }

        public long getIntervalMillis() {
            return this.intervalMillis;
        }
    }

    /*! \brief Engagement manager for article and video engagement.
     *
     * Implemented to handle its own queuing of future executions to accomplish
     * two things:
     *
     * 1. Flushing any engaged time before canceling.
     * 2. Progressive backoff for long engagements to save data.
     */
    private class EngagementManager {

        public Map<String, Object> baseEvent;
        private boolean started;
        private Timer parentTimer;
        private TimerTask waitingTimerTask;
        private long latestDelayMillis, totalTime;
        private Calendar startTime;

        private static final long MAX_TIME_BETWEEN_HEARTBEATS = 60 * 60;
        private static final long OFFSET_MATCHING_BASE_INTERVAL = 35;
        private static final double BACKOFF_PROPORTION = 0.3;


        public EngagementManager(Timer parentTimer, long intervalMillis, Map<String, Object> baseEvent) {
            this.baseEvent = baseEvent;
            this.parentTimer = parentTimer;
            this.latestDelayMillis = intervalMillis;
            this.totalTime = 0;
            this.startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        }

        public boolean isRunning() {
            return this.started;
        }

        public void start() {
            this.scheduleNextExecution(this.latestDelayMillis);
            this.started = true;
            this.startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        }

        public void stop() {
            this.waitingTimerTask.cancel();
            this.started = false;
        }

        public boolean isSameVideo(String url, String urlRef, ParselyVideoMetadata metadata) {
            Map<String, Object> baseMetadata = (Map<String, Object>) this.baseEvent.get("metadata");
            return (this.baseEvent.get("url").equals(url) &&
                    this.baseEvent.get("urlref").equals(urlRef) &&
                    baseMetadata.get("link").equals(metadata.link) &&
                    (int) (baseMetadata.get("duration")) == metadata.durationSeconds);
        }

        private void scheduleNextExecution(long delay) {
            TimerTask task = new TimerTask() {
                public void run() {
                    doEnqueue(this.scheduledExecutionTime());
                    updateLatestInterval();
                    scheduleNextExecution(latestDelayMillis);
                }

                public boolean cancel() {
                    boolean output = super.cancel();
                    // Only enqueue when we actually canceled something. If output is false then
                    // this has already been canceled.
                    if (output == true) {
                        doEnqueue(this.scheduledExecutionTime());
                    }
                    return output;
                }
            };
            this.latestDelayMillis = delay;
            this.parentTimer.schedule(task, delay);
            this.waitingTimerTask = task;
        }

        private void doEnqueue(long scheduledExecutionTime) {
            // Create a copy of the base event to enqueue
            Map<String, Object> event = new HashMap(this.baseEvent);
            PLog(String.format("Enqueuing %s event.", event.get("action")));

            // Update `ts` for the event since it's happening right now.
            Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            Map<String, Object> data = (Map<String, Object>) event.get("data");
            data.put("ts", now.getTimeInMillis() / 1000);

            // Adjust inc by execution time in case we're late or early.
            long executionDiff = (System.currentTimeMillis() - scheduledExecutionTime);
            long inc = (this.latestDelayMillis + executionDiff) / 1000;
            this.totalTime += inc;
            event.put("inc", inc);
            event.put("tt", this.totalTime);

            enqueueEvent(event);
        }

        private void updateLatestInterval() {
            Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            long totalTrackedTime = (now.getTime().getTime() - this.startTime.getTime().getTime()) / 1000;
            double totalWithOffset = totalTrackedTime + this.OFFSET_MATCHING_BASE_INTERVAL;
            double newInterval = totalWithOffset * this.BACKOFF_PROPORTION;
            long clampedNewInterval = (long)Math.min(this.MAX_TIME_BETWEEN_HEARTBEATS, newInterval);
            this.latestDelayMillis = clampedNewInterval * 1000;
        }

        public double getIntervalMillis() {
            return this.latestDelayMillis;
        }
    }
}
