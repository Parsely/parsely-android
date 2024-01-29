package com.parsely.parselyandroid

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.UUID

internal class ParselyTrackerInternal internal constructor(
    siteId: String,
    flushInterval: Int,
    c: Context,
    private val dryRun: Boolean
) : ParselyTracker, EventQueuer {
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
                Logging.log("Flush flushTimer set to %ds", flushManager.intervalMillis / 1000)
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

    private val engagementInterval: Double?
        get() = engagementManager?.intervalMillis

    override val videoEngagementInterval: Double?
        get() = videoEngagementManager?.intervalMillis

    @Suppress("unused") // used via reflection in sample app
    private fun engagementIsActive(): Boolean {
        return engagementManager?.isRunning ?: false
    }

    override fun videoIsActive(): Boolean {
        return videoEngagementManager?.isRunning ?: false
    }

    override val flushInterval: Long
        get() = flushManager.intervalMillis / 1000

    override fun trackPageview(
        url: String,
        urlRef: String,
        urlMetadata: ParselyMetadata?,
        extraData: Map<String, Any>?,
    ) {
        if (url.isBlank()) {
            Logging.log("url cannot be empty")
            return
        }

        val pageViewUuid = generatePixelId()
        lastPageviewUuid = pageViewUuid

        enqueueEvent(
            eventsBuilder.buildEvent(
                url,
                urlRef,
                "pageview",
                urlMetadata,
                extraData,
                pageViewUuid
            )
        )
    }

    override fun startEngagement(
        url: String,
        urlRef: String,
        extraData: Map<String, Any>?
    ) {
        if (url.isBlank()) {
            Logging.log("url cannot be empty")
            return
        }
        val pageViewUuid = lastPageviewUuid
        if (pageViewUuid == null) {
            Logging.log("engagement session cannot start without calling trackPageview first")
            return
        }

        // Cancel anything running
        stopEngagement()

        // Start a new EngagementTask
        val event =
            eventsBuilder.buildEvent(url, urlRef, "heartbeat", null, extraData, pageViewUuid)
        engagementManager = EngagementManager(
            this,
            DEFAULT_ENGAGEMENT_INTERVAL_MILLIS.toLong(),
            event,
            intervalCalculator,
            sdkScope,
            clock
        ).also { it.start() }
    }

    override fun stopEngagement() {
        engagementManager?.let {
            it.stop()
            Logging.log("Engagement session has been stopped")
        }
        engagementManager = null
    }

    override fun trackPlay(
        url: String,
        urlRef: String,
        videoMetadata: ParselyVideoMetadata,
        extraData: Map<String, Any>?,
    ) {
        if (url.isBlank()) {
            Logging.log("url cannot be empty")
            return
        }

        // If there is already an engagement manager for this video make sure it is started.
        videoEngagementManager?.let { manager ->
            if (manager.isSameVideo(url, urlRef, videoMetadata)) {
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
        ).also { it.start() }
    }

    override fun trackPause() {
        videoEngagementManager?.stop()
    }

    override fun resetVideo() {
        videoEngagementManager?.stop()
        videoEngagementManager = null
    }

    /**
     * Add an event Map to the queue.
     * Place a data structure representing the event into the in-memory queue for later use.
     * @param event The event Map to enqueue.
     */
    override fun enqueueEvent(event: Map<String, Any>) {
        // Push it onto the queue
        inMemoryBuffer.add(event)
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
    override fun flushTimerIsActive(): Boolean {
        return flushManager.isRunning
    }

    private fun generatePixelId(): String {
        return UUID.randomUUID().toString()
    }

    private fun flushEvents() {
        flushQueue.invoke(dryRun)
    }

    internal companion object {
        private const val DEFAULT_ENGAGEMENT_INTERVAL_MILLIS = 10500
        @JvmField val ROOT_URL: String = "https://p1.parsely.com/".intern()
    }
}
