package com.example // replace with the project's test package

import com.parsely.parselyandroid.ConversionType
import com.parsely.parselyandroid.ParselyMetadata
import com.parsely.parselyandroid.ParselyTracker
import com.parsely.parselyandroid.ParselyVideoMetadata
import com.parsely.parselyandroid.SiteIdSource

class FakeParselyTracker : ParselyTracker {
    data class VideoPlay(val url: String, val metadata: ParselyVideoMetadata)
    data class Conversion(val url: String, val type: ConversionType, val label: String)

    val trackedPageviews = mutableListOf<String>()
    val engagementStarts = mutableListOf<String>()
    var engagementStopCount = 0
    val conversions = mutableListOf<Conversion>()
    val videoPlays = mutableListOf<VideoPlay>()
    var videoPauseCount = 0
    var videoResetCount = 0

    override fun trackPageview(
        url: String,
        urlRef: String,
        urlMetadata: ParselyMetadata?,
        extraData: Map<String, Any>?,
        siteIdSource: SiteIdSource,
    ) {
        trackedPageviews += url
    }

    override fun startEngagement(
        url: String,
        urlRef: String,
        extraData: Map<String, Any>?,
        siteIdSource: SiteIdSource,
    ) {
        engagementStarts += url
    }

    override fun stopEngagement() {
        engagementStopCount++
    }

    override fun trackPlay(
        url: String,
        urlRef: String,
        videoMetadata: ParselyVideoMetadata,
        extraData: Map<String, Any>?,
        siteIdSource: SiteIdSource,
    ) {
        videoPlays += VideoPlay(url, videoMetadata)
    }

    override fun trackPause() {
        videoPauseCount++
    }

    override fun resetVideo() {
        videoResetCount++
    }

    override fun trackConversion(
        url: String,
        conversionType: ConversionType,
        conversionLabel: String,
        urlRef: String,
        urlMetadata: ParselyMetadata?,
        extraData: Map<String, Any>?,
        siteIdSource: SiteIdSource,
    ) {
        conversions += Conversion(url, conversionType, conversionLabel)
    }
}
