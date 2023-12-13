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

import static com.parsely.parselyandroid.Logging.PLog;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.ProcessLifecycleOwner;

import java.util.Map;
import java.util.UUID;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

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
    @SuppressWarnings("StringOperationCanBeSimplified")
//    static final String ROOT_URL = "http://10.0.2.2:5001/".intern(); // emulator localhost
    static final String ROOT_URL = "https://p1.parsely.com/".intern();
    private boolean isDebug;
    private final FlushManager flushManager;
    private EngagementManager engagementManager, videoEngagementManager;
    @Nullable
    private String lastPageviewUuid = null;
    @NonNull
    private final EventsBuilder eventsBuilder;
    @NonNull
    private final Clock clock;
    @NonNull
    private final HeartbeatIntervalCalculator intervalCalculator;
    @NonNull
    private final InMemoryBuffer inMemoryBuffer;
    @NonNull
    private final FlushQueue flushQueue;

    /**
     * Create a new ParselyTracker instance.
     */
    protected ParselyTracker(String siteId, int flushInterval, Context c) {
        Context context = c.getApplicationContext();
        eventsBuilder = new EventsBuilder(
                new AndroidDeviceInfoRepository(
                        new AdvertisementIdProvider(context, ParselyCoroutineScopeKt.getSdkScope()),
                        new AndroidIdProvider(context)
                ), siteId);
        LocalStorageRepository localStorageRepository = new LocalStorageRepository(context);
        flushManager = new ParselyFlushManager(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                flushEvents();
                return Unit.INSTANCE;
            }
        },  flushInterval * 1000L,
                ParselyCoroutineScopeKt.getSdkScope());
        inMemoryBuffer = new InMemoryBuffer(ParselyCoroutineScopeKt.getSdkScope(), localStorageRepository, () -> {
            if (!flushTimerIsActive()) {
                startFlushTimer();
                PLog("Flush flushTimer set to %ds", (flushManager.getIntervalMillis() / 1000));
            }
            return Unit.INSTANCE;
        });
        flushQueue = new FlushQueue(flushManager, localStorageRepository, new ParselyAPIConnection(ROOT_URL + "mobileproxy"), ParselyCoroutineScopeKt.getSdkScope(), new AndroidConnectivityStatusProvider(context));
        clock = new Clock();
        intervalCalculator = new HeartbeatIntervalCalculator(clock);

        // get the adkey straight away on instantiation
        isDebug = false;

        flushManager.start();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(
                (LifecycleEventObserver) (lifecycleOwner, event) -> {
                    if (event == Lifecycle.Event.ON_STOP) {
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
     * Get the heartbeat interval
     *
     * @return The base engagement tracking interval.
     */
    @Nullable
    public Double getEngagementInterval() {
        if (engagementManager == null) {
            return null;
        }
        return engagementManager.getIntervalMillis();
    }

    @Nullable
    public Double getVideoEngagementInterval() {
        if (videoEngagementManager == null) {
            return null;
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
        engagementManager = new EngagementManager(this, DEFAULT_ENGAGEMENT_INTERVAL_MILLIS, event, intervalCalculator, ParselyCoroutineScopeKt.getSdkScope(), clock );
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
        videoEngagementManager = new EngagementManager(this, DEFAULT_ENGAGEMENT_INTERVAL_MILLIS, hbEvent, intervalCalculator, ParselyCoroutineScopeKt.getSdkScope(), clock);
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
     *
     * @param event The event Map to enqueue.
     */
    void enqueueEvent(Map<String, Object> event) {
        // Push it onto the queue
        inMemoryBuffer.add(event);
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

    @NonNull
    private String generatePixelId() {
        return UUID.randomUUID().toString();
    }

    void flushEvents() {
        flushQueue.invoke(isDebug);
    }
}
