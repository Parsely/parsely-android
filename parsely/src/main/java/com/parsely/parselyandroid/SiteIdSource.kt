package com.parsely.parselyandroid

/**
 * Configuration for the site ID to be used for an event.
 */
public sealed class SiteIdSource {
    /**
     * Instruct SDK to use site ID provided during [ParselyTracker.init].
     */
    public data object Default : SiteIdSource()

    /**
     * Instruct SDK to override the site ID for the event.
     *
     * @param siteId The Parsely public site ID (e.g. "example.com") to use for the event.
     */
    public data class Custom(val siteId: String) : SiteIdSource()
}
