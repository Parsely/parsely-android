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
import kotlin.jvm.Throws

/**
 * Tracks Parse.ly app views in Android apps
 *
 *
 * Accessed as a singleton. Maintains a queue of pageview events in memory and periodically
 * flushes the queue to the Parse.ly pixel proxy server.
 */
public object ParselyTracker {

    private const val DEFAULT_FLUSH_INTERVAL_SECS = 60
    private var instance: ParselyTrackerInternal? = null

    private fun ensureInitialized(): ParselyTrackerInternal {
        return instance ?: run {
            throw ParselyNotInitializedException("Parse.ly client has not been initialized. Call ParselyTracker#init before using the SDK.");
        }
    }

    public val engagementInterval: Double?
        @JvmStatic
        get() = ensureInitialized().engagementInterval

    public val videoEngagementInterval: Double?
        @JvmStatic
        get() = ensureInitialized().videoEngagementInterval

    public val flushInterval: Long
        @JvmStatic
        get() = ensureInitialized().flushInterval

    @JvmStatic
    public fun engagementIsActive(): Boolean = ensureInitialized().engagementIsActive()

    @JvmStatic
    public fun videoIsActive(): Boolean = ensureInitialized().videoIsActive()

    @JvmStatic
    public fun trackPageview(
        url: String,
        urlRef: String = "",
        urlMetadata: ParselyMetadata? = null,
        extraData: Map<String, Any>? = null,
    ): Unit = ensureInitialized().trackPageview(url, urlRef, urlMetadata, extraData)

    @JvmStatic
    public fun startEngagement(
        url: String,
        urlRef: String = "",
        extraData: Map<String, Any>? = null
    ): Unit = ensureInitialized().startEngagement(url, urlRef, extraData)

    @JvmStatic
    public fun stopEngagement(): Unit = ensureInitialized().stopEngagement()

    @JvmStatic
    public fun trackPlay(
        url: String,
        urlRef: String = "",
        videoMetadata: ParselyVideoMetadata,
        extraData: Map<String, Any>? = null,
    ): Unit = ensureInitialized().trackPlay(url, urlRef, videoMetadata, extraData)

    @JvmStatic
    public fun trackPause(): Unit = ensureInitialized().trackPause()

    @JvmStatic
    public fun resetVideo(): Unit = ensureInitialized().resetVideo()

    @JvmStatic
    public fun flushTimerIsActive(): Boolean = ensureInitialized().flushTimerIsActive()

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
}
