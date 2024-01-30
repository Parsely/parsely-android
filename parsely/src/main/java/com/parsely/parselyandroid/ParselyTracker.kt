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
import org.jetbrains.annotations.TestOnly

/**
 * Tracks Parse.ly app views in Android apps
 *
 * Accessed as a singleton. Maintains a queue of pageview events in memory and periodically
 * flushes the queue to the Parse.ly pixel proxy server.
 */
public interface ParselyTracker {

    /**
     * Returns whether video tracking is active.
     *
     * @return Whether video tracking is active.
     */
    public fun videoIsActive(): Boolean

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
        urlRef: String = "",
        urlMetadata: ParselyMetadata? = null,
        extraData: Map<String, Any>? = null,
    )

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
    public fun startEngagement(
        url: String,
        urlRef: String = "",
        extraData: Map<String, Any>? = null
    )

    /**
     * Stop engaged time tracking.
     *
     *
     * Stops the engaged time tracker, sending any accumulated engaged time to Parse.ly.
     * NOTE: This **must** be called in your `MainActivity` during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    public fun stopEngagement()

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
    public fun trackPlay(
        url: String,
        urlRef: String = "",
        videoMetadata: ParselyVideoMetadata,
        extraData: Map<String, Any>? = null,
    )

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
    public fun trackPause()

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
    public fun resetVideo()

    public fun flushTimerIsActive(): Boolean

    public companion object {
        private const val DEFAULT_FLUSH_INTERVAL_SECS = 60
        private var instance: ParselyTrackerInternal? = null

        private fun ensureInitialized(): ParselyTracker {
            return instance ?: run {
                throw ParselyNotInitializedException()
            }
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
        public fun init(
            siteId: String,
            flushInterval: Int = DEFAULT_FLUSH_INTERVAL_SECS,
            context: Context,
            dryRun: Boolean = false,
        ) {
            if (instance != null) {
                throw ParselyAlreadyInitializedException()
            }
            instance = ParselyTrackerInternal(siteId, flushInterval, context, dryRun)
        }

        @JvmStatic
        public fun sharedInstance(): ParselyTracker = ensureInitialized()

        @TestOnly
        internal fun tearDown() {
            instance = null
        }
    }
}
