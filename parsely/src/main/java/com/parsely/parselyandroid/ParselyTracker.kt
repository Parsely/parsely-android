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

/**
 * Tracks Parse.ly app views in Android apps
 *
 *
 * Accessed as a singleton. Maintains a queue of pageview events in memory and periodically
 * flushes the queue to the Parse.ly pixel proxy server.
 */
public interface ParselyTracker {
    public val engagementInterval: Double?
    public val videoEngagementInterval: Double?
    public val flushInterval: Long

    public fun engagementIsActive(): Boolean
    public fun videoIsActive(): Boolean

    public fun trackPageview(
        url: String,
        urlRef: String = "",
        urlMetadata: ParselyMetadata? = null,
        extraData: Map<String, Any>? = null,
    )

    public fun startEngagement(
        url: String,
        urlRef: String = "",
        extraData: Map<String, Any>? = null
    )

    public fun stopEngagement()

    public fun trackPlay(
        url: String,
        urlRef: String = "",
        videoMetadata: ParselyVideoMetadata,
        extraData: Map<String, Any>? = null,
    )
    public fun trackPause()
    public fun resetVideo()
    public fun flushEventQueue()
    public fun flushTimerIsActive(): Boolean

    public companion object {
        private const val DEFAULT_FLUSH_INTERVAL_SECS = 60
        private var instance: ParselyTrackerInternal? = null

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
                val newInstance = ParselyTrackerInternal(siteId, flushInterval, context, dryRun)
                instance = newInstance
                return newInstance
            }
        }
    }
}
