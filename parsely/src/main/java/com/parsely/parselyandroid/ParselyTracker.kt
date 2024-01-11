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
public open class ParselyTracker protected constructor(
    siteId: String,
    flushInterval: Int,
    c: Context,
    private val dryRun: Boolean
) {
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
        clock = Clock()
        eventsBuilder = EventsBuilder(
            AndroidDeviceInfoRepository(
                AdvertisementIdProvider(context, sdkScope),
                AndroidIdProvider(context)
            ), siteId, clock
        )
        val localStorageRepository = LocalStorageRepository(context)
        flushManager = ParselyFlushManager(
            {
                flushEvents()
            }, flushInterval * 1000L,
            sdkScope
        )
        inMemoryBuffer = InMemoryBuffer(sdkScope, localStorageRepository) {
            if (!flushTimerIsActive()) {
                startFlushTimer()
                log("Flush flushTimer set to %ds", flushManager.intervalMillis / 1000)
            }
        }
        flushQueue = FlushQueue(
            flushManager,
            localStorageRepository,
            ParselyAPIConnection(ROOT_URL + "mobileproxy"),
            sdkScope,
            AndroidConnectivityStatusProvider(context)
        )
        intervalCalculator = HeartbeatIntervalCalculator(clock)

        flushManager.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event: Lifecycle.Event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    flushEvents()
                }
            }
        )
    }

    /**
     * Get the heartbeat interval
     *
     * @return The base engagement tracking interval.
     */
    public val engagementInterval: Double?
        get() = engagementManager?.intervalMillis

    public val videoEngagementInterval: Double?
        get() = videoEngagementManager?.intervalMillis

    /**
     * Returns whether the engagement tracker is running.
     *
     * @return Whether the engagement tracker is running.
     */
    public fun engagementIsActive(): Boolean {
        return engagementManager?.isRunning ?: false
    }

    /**
     * Returns whether video tracking is active.
     *
     * @return Whether video tracking is active.
     */
    public fun videoIsActive(): Boolean {
        return videoEngagementManager?.isRunning ?: false
    }

    /**
     * Returns the interval at which the event queue is flushed to Parse.ly.
     *
     * @return The interval at which the event queue is flushed to Parse.ly.
     */
    public val flushInterval: Long
        get() = flushManager.intervalMillis / 1000

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
    public fun trackPageview(
        url: String,
        urlRef: String? = null,
        urlMetadata: ParselyMetadata? = null,
        extraData: Map<String, Any>? = null,
    ) {
        if (url.isBlank()) {
            log("url cannot be empty")
            return
        }

        val pageViewUuid = generatePixelId()
        lastPageviewUuid = pageViewUuid

        enqueueEvent(
            eventsBuilder.buildEvent(
                url,
                urlRef.orEmpty(),
                "pageview",
                urlMetadata,
                extraData,
                pageViewUuid
            )
        )
    }

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
    public fun startEngagement(
        url: String,
        urlRef: String? = null,
        extraData: Map<String, Any>? = null
    ) {
        if (url.isBlank()) {
            log("url cannot be empty")
            return
        }
        val pageViewUuid = lastPageviewUuid
        if (pageViewUuid == null) {
            log("engagement session cannot start without calling trackPageview first")
            return
        }

        // Cancel anything running
        stopEngagement()

        // Start a new EngagementTask
        val event =
            eventsBuilder.buildEvent(url, urlRef.orEmpty(), "heartbeat", null, extraData, pageViewUuid)
        engagementManager = EngagementManager(
            this,
            DEFAULT_ENGAGEMENT_INTERVAL_MILLIS.toLong(),
            event,
            intervalCalculator,
            sdkScope,
            clock
        ).also { it.start() }
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
    public fun stopEngagement() {
        engagementManager?.stop()
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
    @JvmOverloads
    public fun trackPlay(
        url: String,
        urlRef: String? = null,
        videoMetadata: ParselyVideoMetadata,
        extraData: Map<String, Any>? = null,
    ) {
        if (url.isBlank()) {
            log("url cannot be empty")
            return
        }

        // If there is already an engagement manager for this video make sure it is started.
        videoEngagementManager?.let { manager ->
            if (manager.isSameVideo(url, urlRef.orEmpty(), videoMetadata)) {
                if (!manager.isRunning) {
                    manager.start()
                }
                return // all done here. early exit.
            } else {
                // Different video. Stop and remove it so we can start fresh.
                manager.stop()
            }
        }
        val uuid = generatePixelId()

        // Enqueue the videostart
        val videostartEvent =
            eventsBuilder.buildEvent(url, urlRef.orEmpty(), "videostart", videoMetadata, extraData, uuid)
        enqueueEvent(videostartEvent)

        // Start a new engagement manager for the video.
        val hbEvent =
            eventsBuilder.buildEvent(url, urlRef.orEmpty(), "vheartbeat", videoMetadata, extraData, uuid)
        // TODO: Can we remove some metadata fields from this request?
        videoEngagementManager = EngagementManager(
            this,
            DEFAULT_ENGAGEMENT_INTERVAL_MILLIS.toLong(),
            hbEvent,
            intervalCalculator,
            sdkScope,
            clock
        ).also { it.start() }
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
    public fun trackPause() {
        videoEngagementManager?.stop()
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
    public fun resetVideo() {
        videoEngagementManager?.stop()
        videoEngagementManager = null
    }

    /**
     * Add an event Map to the queue.
     * Place a data structure representing the event into the in-memory queue for later use.
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
    @Deprecated("The SDK now automatically flushes the queue on app lifecycle events. Any usage of this method is safe to remove and will have no effect")
    public fun flushEventQueue() {
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
    private fun startFlushTimer() {
        flushManager.start()
    }

    /**
     * Returns whether the event queue flush timer is running.
     *
     * @return Whether the event queue flush timer is running.
     */
    public fun flushTimerIsActive(): Boolean {
        return flushManager.isRunning
    }

    private fun generatePixelId(): String {
        return UUID.randomUUID().toString()
    }

    private fun flushEvents() {
        flushQueue.invoke(dryRun)
    }

    public companion object {
        private var instance: ParselyTracker? = null
        private const val DEFAULT_FLUSH_INTERVAL_SECS = 60
        private const val DEFAULT_ENGAGEMENT_INTERVAL_MILLIS = 10500
        @JvmField public val ROOT_URL: String = "https://p1.parsely.com/".intern()

        /**
         * Singleton instance accessor. Note: This must be called after [.sharedInstance]
         *
         * @return The singleton instance
         */
        @JvmStatic
        public fun sharedInstance(): ParselyTracker? {
            return instance
        }

        /**
         * Singleton instance factory Note: this must be called before [.sharedInstance]
         *
         * @param siteId        The Parsely public site id (eg "example.com")
         * @param flushInterval The interval at which the events queue should flush, in seconds
         * @param context             The current Android application context
         * @param dryRun If set to `true`, events **won't** be sent to Parse.ly server
         * @return The singleton instance
         */
        @JvmStatic
        @JvmOverloads
        public fun sharedInstance(
            siteId: String,
            flushInterval: Int = DEFAULT_FLUSH_INTERVAL_SECS,
            context: Context,
            dryRun: Boolean = false,
        ): ParselyTracker {
            return instance ?: run {
                val newInstance = ParselyTracker(siteId, flushInterval, context, dryRun)
                instance = newInstance
                return newInstance
            }
        }
    }
}
