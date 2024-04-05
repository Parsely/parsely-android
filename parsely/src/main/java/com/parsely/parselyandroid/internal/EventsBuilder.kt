package com.parsely.parselyandroid.internal

import com.parsely.parselyandroid.internal.Logging.log
import com.parsely.parselyandroid.ParselyMetadata

internal class EventsBuilder(
    private val deviceInfoRepository: DeviceInfoRepository,
    private val siteId: String,
    private val clock: Clock,
) {
    /**
     * Create an event Map
     *
     * @param url       The URL identifying the pageview/heartbeat
     * @param action    Action to use (e.g. pageview, heartbeat, videostart, vheartbeat)
     * @param metadata  Metadata to attach to the event.
     * @param extraData A Map of additional information to send with the event.
     * @return A Map object representing the event to be sent to Parse.ly.
     */
    fun buildEvent(
        url: String,
        urlRef: String,
        action: String,
        metadata: ParselyMetadata?,
        extraData: Map<String, Any>?,
        uuid: String
    ): Map<String, Any> {
        log("buildEvent called for %s/%s", action, url)

        // Main event info
        val event: MutableMap<String, Any> = HashMap()
        event["url"] = url
        event["urlref"] = urlRef
        event["idsite"] = siteId
        event["action"] = action

        // Make a copy of extraData and add some things.
        val data: MutableMap<String, Any> = HashMap()
        if (extraData != null) {
            data.putAll(extraData)
        }
        val deviceInfo = deviceInfoRepository.collectDeviceInfo()
        data["ts"] = clock.now.inWholeMilliseconds
        data.putAll(deviceInfo)
        event["data"] = data
        metadata?.let {
            event["metadata"] = it.toMap()
        }
        if (action == "videostart" || action == "vheartbeat") {
            event[VIDEO_START_ID_KEY] = uuid
        }
        if (action == "pageview" || action == "heartbeat") {
            event[PAGE_VIEW_ID_KEY] = uuid
        }
        return event
    }

    companion object {
        private const val VIDEO_START_ID_KEY = "vsid"
        private const val PAGE_VIEW_ID_KEY = "pvid"
    }
}
