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
package com.parsely.parselyandroid

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.parsely.parselyandroid.Logging.log
import java.util.UUID

/**
 * Tracks Parse.ly app views in Android apps
 *
 *
 * Accessed as a singleton. Maintains a queue of pageview events in memory and periodically
 * flushes the queue to the Parse.ly pixel proxy server.
 */
open class ParselyTracker protected constructor(siteId: String?, flushInterval: Int, c: Context) {
    private var isDebug: Boolean
    private val flushManager: FlushManager
    private var engagementManager: EngagementManager? = null
    private var videoEngagementManager: EngagementManager? = null
    private var lastPageviewUuid: String? = null
    private val eventsBuilder: EventsBuilder
    private val clock: Clock
    private val intervalCalculator: HeartbeatIntervalCalculator
    private val inMemoryBuffer: InMemoryBuffer
    private val flushQueue: FlushQueue

    /**
     * Create a new ParselyTracker instance.
     */
    init {
        val context = c.applicationContext
        eventsBuilder = EventsBuilder(
            AndroidDeviceInfoRepository(
                AdvertisementIdProvider(context, sdkScope),
                AndroidIdProvider(context)
            ), siteId!!
        )
        val localStorageRepository = LocalStorageRepository(context)
        flushManager = ParselyFlushManager(
            {
                flushEvents()
                Unit
            }, flushInterval * 1000L,
            sdkScope
        )
        inMemoryBuffer = InMemoryBuffer(sdkScope, localStorageRepository) {
            if (!flushTimerIsActive()) {
                startFlushTimer()
                log("Flush flushTimer set to %ds", flushManager.intervalMillis / 1000)
            }
            Unit
        }
        flushQueue = FlushQueue(
            flushManager,
            localStorageRepository,
            ParselyAPIConnection(ROOT_URL + "mobileproxy"),
            sdkScope,
            AndroidConnectivityStatusProvider(context)
        )
        clock = Clock()
        intervalCalculator = HeartbeatIntervalCalculator(clock)

        // get the adkey straight away on instantiation
        isDebug = false
        flushManager.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { lifecycleOwner: LifecycleOwner?, event: Event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    flushEvents()
                }
            }
        )
    }

    val engagementInterval: Double?
        /**
         * Get the heartbeat interval
         *
         * @return The base engagement tracking interval.
         */
        get() = if (engagementManager == null) {
            null
        } else engagementManager!!.intervalMillis
    val videoEngagementInterval: Double?
        get() = if (videoEngagementManager == null) {
            null
        } else videoEngagementManager!!.intervalMillis

    /**
     * Returns whether the engagement tracker is running.
     *
     * @return Whether the engagement tracker is running.
     */
    fun engagementIsActive(): Boolean {
        return engagementManager != null && engagementManager!!.isRunning
    }

    /**
     * Returns whether video tracking is active.
     *
     * @return Whether video tracking is active.
     */
    fun videoIsActive(): Boolean {
        return videoEngagementManager != null && videoEngagementManager!!.isRunning
    }

    val flushInterval: Long
        /**
         * Returns the interval at which the event queue is flushed to Parse.ly.
         *
         * @return The interval at which the event queue is flushed to Parse.ly.
         */
        get() = flushManager.intervalMillis / 1000

    /**
     * Set a debug flag which will prevent data from being sent to Parse.ly
     *
     *
     * Use this flag when developing to prevent the SDK from actually sending requests
     * to Parse.ly servers. The value it would otherwise send is logged to the console.
     *
     * @param debug Value to use for debug flag.
     */
    fun setDebug(debug: Boolean) {
        isDebug = debug
        log("Debugging is now set to $isDebug")
    }

    /**
     * Register a pageview event using a URL and optional metadata.
     *
     * @param url         The URL of the article being tracked
     * (eg: "http://example.com/some-old/article.html")
     * @param urlRef      Referrer URL associated with this video view.
     * @param urlMetadata Optional metadata for the URL -- not used in most cases. Only needed
     * when `url` isn't accessible over the Internet (i.e. app-only
     * content). Do not use this for **content also hosted on** URLs Parse.ly
     * would normally crawl.
     * @param extraData   A Map of additional information to send with the event.
     */
    fun trackPageview(
        url: String,
        urlRef: String?,
        urlMetadata: ParselyMetadata?,
        extraData: Map<String?, Any?>?
    ) {
        var urlRef = urlRef
        if (url.equals("")) {
            log("url cannot be empty");
            return;
        }

        // Blank urlref is better than null
        if (urlRef == null) {
            urlRef = ""
        }
        lastPageviewUuid = generatePixelId()
        enqueueEvent(
            eventsBuilder.buildEvent(
                url,
                urlRef,
                "pageview",
                urlMetadata,
                extraData,
                lastPageviewUuid
            )
        )
    }
    /**
     * Same as [.startEngagement] but with extra data.
     *
     * @param url       The URL to track engaged time for.
     * @param urlRef    Referrer URL associated with this video view.
     * @param extraData A Map of additional information to send with the event.
     */
    /**
     * Start engaged time tracking for the given URL.
     *
     *
     * This starts a timer which will send events to Parse.ly on a regular basis
     * to capture engaged time for this URL. The value of `url` should be a URL for
     * which `trackPageview` has been called.
     *
     * @param url    The URL to track engaged time for.
     * @param urlRef Referrer URL associated with this video view.
     */
    @JvmOverloads
    fun startEngagement(
        url: String,
        urlRef: String?,
        extraData: Map<String?, Any?>? = null
    ) {
        var urlRef = urlRef
        if (url.equals("")) {
            log("url cannot be empty");
            return;
        }

        // Blank urlref is better than null
        if (urlRef == null) {
            urlRef = ""
        }
        // Cancel anything running
        stopEngagement()

        // Start a new EngagementTask
        val event =
            eventsBuilder.buildEvent(url, urlRef, "heartbeat", null, extraData, lastPageviewUuid)
        engagementManager = EngagementManager(
            this,
            DEFAULT_ENGAGEMENT_INTERVAL_MILLIS.toLong(),
            event,
            intervalCalculator,
            sdkScope,
            clock
        )
        engagementManager!!.start()
    }

    /**
     * Stop engaged time tracking.
     *
     *
     * Stops the engaged time tracker, sending any accumulated engaged time to Parse.ly.
     * NOTE: This **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    fun stopEngagement() {
        if (engagementManager == null) {
            return
        }
        engagementManager!!.stop()
        engagementManager = null
    }

    /**
     * Start video tracking.
     *
     *
     * Starts tracking view time for a video being viewed at a given url. Will send a `videostart`
     * event unless the same url/videoId had previously been paused.
     * Video metadata must be provided, specifically the video ID and video duration.
     *
     *
     * The `url` value is *not* the URL of a video, but the post which contains the video. If the video
     * is not embedded in a post, then this should contain a well-formatted URL on the customer's
     * domain (e.g. http://<CUSTOMERDOMAIN>/app-videos). This URL doesn't need to return a 200 status
     * when crawled, but must but well-formatted so Parse.ly systems recognize it as belonging to
     * the customer.
     *
     * @param url           URL of post the video is embedded in. If videos is not embedded, a
     * valid URL for the customer should still be provided.
     * (e.g. http://<CUSTOMERDOMAIN>/app-videos)
     * @param urlRef        Referrer URL associated with this video view.
     * @param videoMetadata Metadata about the video being tracked.
     * @param extraData     A Map of additional information to send with the event.
    </CUSTOMERDOMAIN></CUSTOMERDOMAIN> */
    fun trackPlay(
        url: String,
        urlRef: String?,
        videoMetadata: ParselyVideoMetadata,
        extraData: Map<String?, Any?>?
    ) {
        var urlRef = urlRef
        if (url.equals("")) {
            log("url cannot be empty");
            return;
        }
        // Blank urlref is better than null
        if (urlRef == null) {
            urlRef = ""
        }

        // If there is already an engagement manager for this video make sure it is started.
        if (videoEngagementManager != null) {
            videoEngagementManager =
                if (videoEngagementManager!!.isSameVideo(url, urlRef, videoMetadata)) {
                    if (!videoEngagementManager!!.isRunning) {
                        videoEngagementManager!!.start()
                    }
                    return  // all done here. early exit.
                } else {
                    // Different video. Stop and remove it so we can start fresh.
                    videoEngagementManager!!.stop()
                    null
                }
        }
        val uuid = generatePixelId()

        // Enqueue the videostart
        val videostartEvent =
            eventsBuilder.buildEvent(url, urlRef, "videostart", videoMetadata, extraData, uuid)
        enqueueEvent(videostartEvent)

        // Start a new engagement manager for the video.
        val hbEvent =
            eventsBuilder.buildEvent(url, urlRef, "vheartbeat", videoMetadata, extraData, uuid)
        // TODO: Can we remove some metadata fields from this request?
        videoEngagementManager = EngagementManager(
            this,
            DEFAULT_ENGAGEMENT_INTERVAL_MILLIS.toLong(),
            hbEvent,
            intervalCalculator,
            sdkScope,
            clock
        )
        videoEngagementManager!!.start()
    }

    /**
     * Pause video tracking.
     *
     *
     * Pauses video tracking for an ongoing video. If [.trackPlay] is immediately called again for
     * the same video, a new video start event will not be sent. This models a user pausing a
     * playing video.
     *
     *
     * NOTE: This or [.resetVideo] **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    fun trackPause() {
        if (videoEngagementManager == null) {
            return
        }
        videoEngagementManager!!.stop()
    }

    /**
     * Reset tracking on a video.
     *
     *
     * Stops video tracking and resets internal state for the video. If [.trackPlay] is immediately
     * called for the same video, a new video start event is set. This models a user stopping a
     * video and (on [.trackPlay] being called again) starting it over.
     *
     *
     * NOTE: This or [.trackPause] **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    fun resetVideo() {
        if (videoEngagementManager == null) {
            return
        }
        videoEngagementManager!!.stop()
        videoEngagementManager = null
    }

    /**
     * Add an event Map to the queue.
     *
     *
     * Place a data structure representing the event into the in-memory queue for later use.
     *
     *
     *
     * @param event The event Map to enqueue.
     */
    internal open fun enqueueEvent(event: Map<String, Any>) {
        // Push it onto the queue
        inMemoryBuffer.add(event)
    }

    /**
     * Deprecated since 3.1.1. The SDK now automatically flushes the queue on app lifecycle events.
     * Any usage of this method is safe to remove and will have no effect. Keeping for backwards compatibility.
     */
    @Deprecated("")
    fun flushEventQueue() {
        // no-op
    }

    /**
     * Start the timer to flush events to Parsely.
     *
     *
     * Instantiates the callback timer responsible for flushing the events queue.
     * Can be called before of after `stop`, but has no effect if used before instantiating the
     * singleton
     */
    fun startFlushTimer() {
        flushManager.start()
    }

    /**
     * Returns whether the event queue flush timer is running.
     *
     * @return Whether the event queue flush timer is running.
     */
    fun flushTimerIsActive(): Boolean {
        return flushManager.isRunning
    }

    private fun generatePixelId(): String {
        return UUID.randomUUID().toString()
    }

    fun flushEvents() {
        flushQueue.invoke(isDebug)
    }

    companion object {
        private var instance: ParselyTracker? = null
        private const val DEFAULT_FLUSH_INTERVAL_SECS = 60
        private const val DEFAULT_ENGAGEMENT_INTERVAL_MILLIS = 10500
        @JvmField val ROOT_URL = "https://p1.parsely.com/".intern()

        /**
         * Singleton instance accessor. Note: This must be called after [.sharedInstance]
         *
         * @return The singleton instance
         */
        @JvmStatic
        fun sharedInstance(): ParselyTracker? {
            return if (instance == null) {
                null
            } else instance
        }

        /**
         * Singleton instance factory Note: this must be called before [.sharedInstance]
         *
         * @param siteId The Parsely public site id (eg "example.com")
         * @param c      The current Android application context
         * @return The singleton instance
         */
        fun sharedInstance(siteId: String?, c: Context): ParselyTracker? {
            return sharedInstance(siteId, DEFAULT_FLUSH_INTERVAL_SECS, c)
        }

        /**
         * Singleton instance factory Note: this must be called before [.sharedInstance]
         *
         * @param siteId        The Parsely public site id (eg "example.com")
         * @param flushInterval The interval at which the events queue should flush, in seconds
         * @param c             The current Android application context
         * @return The singleton instance
         */
        @JvmStatic
        fun sharedInstance(siteId: String?, flushInterval: Int, c: Context): ParselyTracker? {
            if (instance == null) {
                instance = ParselyTracker(siteId, flushInterval, c)
            }
            return instance
        }
    }
}
