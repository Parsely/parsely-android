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
 * Accessed as a singleton. Maintains a queue of events in memory and periodically
 * flushes the queue to the Parse.ly pixel proxy server.
 */
public interface ParselyTracker {

    /**
     * Register a pageview event using a URL and optional metadata. You should only call this method once per page view.
     *
     * @param url         The URL of the article being tracked (eg: "http://example.com/some-old/article.html")
     * @param urlRef      The url of the page that linked to the viewed page. Analogous to HTTP referer
     * @param urlMetadata Optional metadata for the URL -- not used in most cases. Only needed
     *                    when `url` isn't accessible over the Internet (i.e. app-only
     *                    content). Do not use this for **content also hosted on** URLs Parse.ly
     *                    would normally crawl.
     * @param extraData   A Map of additional information to send with the event.
     * @param siteIdSource The source of the site ID to use for the event. If [SiteIdSource.Default],
     *                     the site ID provided during [init] will be used. Otherwise, the site ID
     *                     provided in the [SiteIdSource.Custom] object will be used.
     */
    public fun trackPageview(
        url: String,
        urlRef: String = "",
        urlMetadata: ParselyMetadata? = null,
        extraData: Map<String, Any>? = null,
        siteIdSource: SiteIdSource = SiteIdSource.Default,
    )

    /**
     * Start engaged time tracking for the given URL.
     *
     * Start engaged time tracking for the given URL. Once called, the Parse.ly tracking script
     * will automatically send `heartbeat` events periodically to capture engaged time for this url
     * until engaged time tracking stops.
     *
     * This call also automatically stops tracking engaged time for any urls that are not
     * the current url.
     *
     * The value of `url` should be a URL for which [trackPageview] has been called.
     *
     * @param url    The URL of the tracked article (eg: “http://example.com/some-old/article.html“)
     * @param urlRef The url of the page that linked to the page being engaged with. Analogous to HTTP referer
     * @param extraData A map of additional information to send with the generated `heartbeat` events
     * @param siteIdSource The source of the site ID to use for the event.
     */
    public fun startEngagement(
        url: String,
        urlRef: String = "",
        extraData: Map<String, Any>? = null,
        siteIdSource: SiteIdSource = SiteIdSource.Default,
    )

    /**
     * Stops the engaged time tracker, sending any accumulated engaged time to Parse.ly.
     *
     * NOTE: This **must** be called during various Android lifecycle events like
     * `onPause` or `onStop`. Otherwise, engaged time tracking may keep running
     * in the background and Parse.ly values may be inaccurate.
     */
    public fun stopEngagement()

    /**
     * Start video tracking.
     *
     * This starts tracking view time for a video that someone is watching at a given url.
     * It will send a `videostart` event unless the same url/videoId had previously been paused.
     * Video metadata must be provided, specifically the video ID and video duration.
     *
     * The `url` value is *not* the URL of a video, but the post which contains the video. If the video
     * is not embedded in a post, then this should contain a well-formatted URL on the customer's
     * domain (e.g. http://example.com/app-videos). This URL doesn't need to return a 200 status
     * when crawled, but must but well-formatted so Parse.ly systems recognize it as belonging to
     * the customer.
     *
     * If a video is already being tracked when this method is called, unless it's the same video,
     * the existing video tracking will be stopped and a new `videostart` event will be sent for the new video.
     *
     * @param url           URL of post with the embedded video. If you haven’t embedded the video,
     *                      then send a valid URL matching your domain. (e.g. http://example.com/app-videos)
     * @param urlRef        The url of the page that linked to the page being engaged with. Analogous to HTTP referer
     * @param videoMetadata Metadata about the tracked video
     * @param extraData     A Map of additional information to send with the event
     * @param siteIdSource The source of the site ID to use for the event.
     */
    public fun trackPlay(
        url: String,
        urlRef: String = "",
        videoMetadata: ParselyVideoMetadata,
        extraData: Map<String, Any>? = null,
        siteIdSource: SiteIdSource = SiteIdSource.Default,
    )

    /**
     * Pause video tracking for an ongoing video. If [trackPlay] is immediately called again
     * for the same video, a new `videostart` event will not be sent. This models a user pausing
     * a playing video.
     *
     *
     * NOTE: This or [resetVideo] **must** be called during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    public fun trackPause()

    /**
     * Stops video tracking and resets internal state for the video.
     * If [trackPlay] is immediately called for the same video, a new `videostart` event is set.
     * This models a user stopping a video and (on [trackPlay] being called again) starting it over.
     *
     * NOTE: This or [trackPause] **must** be called during various Android lifecycle events
     * like `onPause` or `onStop`. Otherwise, engaged time tracking may keep running in the background
     * and Parse.ly values may be inaccurate.
     */
    public fun resetVideo()

    public companion object {
        private const val DEFAULT_FLUSH_INTERVAL_SECS = 60
        private var instance: ParselyTrackerInternal? = null

        private fun ensureInitialized(): ParselyTracker {
            return instance ?: run {
                throw ParselyNotInitializedException()
            }
        }

        /**
         * Singleton instance factory. This **must** be called before [sharedInstance]
         * If this method is called when an instance already exists, a [ParselyAlreadyInitializedException] will be thrown.
         *
         *
         * @param siteId       The Parsely public site id (eg "example.com")
         * @param flushInterval The interval at which the events queue should flush, in seconds
         * @param context      The current Android application context
         * @param dryRun       If set to `true`, events **won't** be sent to Parse.ly server
         * @return             The singleton instance
         * @throws             ParselyAlreadyInitializedException if the ParselyTracker instance is already initialized.
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

        /**
         * Returns the singleton instance of the ParselyTracker.
         *
         * This method **must** be called after the [init] method.
         * If the [init] method hasn't been called before this method, a [ParselyNotInitializedException] will be thrown.
         *
         * @return The singleton instance of ParselyTracker.
         * @throws ParselyNotInitializedException if the ParselyTracker instance is not initialized.
         */
        @JvmStatic
        public fun sharedInstance(): ParselyTracker = ensureInitialized()

        @TestOnly
        internal fun tearDown() {
            instance = null
        }
    }
}
