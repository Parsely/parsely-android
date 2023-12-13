package com.parsely.parselyandroid;

import static com.parsely.parselyandroid.Logging.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

class EventsBuilder {
    private static final String VIDEO_START_ID_KEY = "vsid";
    private static final String PAGE_VIEW_ID_KEY = "pvid";

    private final String siteId;

    @NonNull
    private final DeviceInfoRepository deviceInfoRepository;

    public EventsBuilder(@NonNull final DeviceInfoRepository deviceInfoRepository, @NonNull final String siteId) {
        this.siteId = siteId;
        this.deviceInfoRepository = deviceInfoRepository;
    }

    /**
     * Create an event Map
     *
     * @param url       The URL identifying the pageview/heartbeat
     * @param action    Action to use (e.g. pageview, heartbeat, videostart, vheartbeat)
     * @param metadata  Metadata to attach to the event.
     * @param extraData A Map of additional information to send with the event.
     * @return A Map object representing the event to be sent to Parse.ly.
     */
    @NonNull
    Map<String, Object> buildEvent(
            String url,
            String urlRef,
            String action,
            ParselyMetadata metadata,
            Map<String, Object> extraData,
            @Nullable String uuid
    ) {
        log("buildEvent called for %s/%s", action, url);

        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        // Main event info
        Map<String, Object> event = new HashMap<>();
        event.put("url", url);
        event.put("urlref", urlRef);
        event.put("idsite", siteId);
        event.put("action", action);

        // Make a copy of extraData and add some things.
        Map<String, Object> data = new HashMap<>();
        if (extraData != null) {
            data.putAll(extraData);
        }

        final Map<String, String> deviceInfo = deviceInfoRepository.collectDeviceInfo();
        data.put("ts", now.getTimeInMillis());
        data.putAll(deviceInfo);

        event.put("data", data);

        if (metadata != null) {
            event.put("metadata", metadata.toMap());
        }

        if (action.equals("videostart") || action.equals("vheartbeat")) {
            event.put(VIDEO_START_ID_KEY, uuid);
        }

        if (action.equals("pageview") || action.equals("heartbeat")) {
            event.put(PAGE_VIEW_ID_KEY, uuid);
        }

        return event;
    }

}
