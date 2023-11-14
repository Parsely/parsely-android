package com.parsely.parselyandroid;

import static com.parsely.parselyandroid.ParselyTracker.PLog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

class EventsBuilder {
    private static final String UUID_KEY = "parsely-uuid";
    private static final String VIDEO_START_ID_KEY = "vsid";
    private static final String PAGE_VIEW_ID_KEY = "pvid";

    @NonNull
    private final Context context;
    private final SharedPreferences settings;
    private final String siteId;

    private String adKey = null;

    public EventsBuilder(@NonNull final Context context, @NonNull final String siteId) {
        this.context = context;
        this.siteId = siteId;
        settings = context.getSharedPreferences("parsely-prefs", 0);
        new GetAdKey(context).execute();
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
        PLog("buildEvent called for %s/%s", action, url);

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

        final Map<String, String> deviceInfo = collectDeviceInfo();
        data.put("manufacturer", deviceInfo.get("manufacturer"));
        data.put("os", deviceInfo.get("os"));
        data.put("os_version", deviceInfo.get("os_version"));
        data.put("ts", now.getTimeInMillis());
        data.put("parsely_site_uuid", deviceInfo.get("parsely_site_uuid"));
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

    /**
     * Collect device-specific info.
     * <p>
     * Collects info about the device and user to use in Parsely events.
     */
    private Map<String, String> collectDeviceInfo() {
        Map<String, String> dInfo = new HashMap<>();

        // TODO: screen dimensions (maybe?)
        dInfo.put("parsely_site_uuid", getParselySiteUuid());
        dInfo.put("manufacturer", android.os.Build.MANUFACTURER);
        dInfo.put("os", "android");
        dInfo.put("os_version", String.format("%d", android.os.Build.VERSION.SDK_INT));

        // FIXME: Not passed in event or used anywhere else.
        CharSequence txt = context.getPackageManager().getApplicationLabel(context.getApplicationInfo());
        dInfo.put("appname", txt.toString());

        return dInfo;
    }

    private String getParselySiteUuid() {
        PLog("adkey is: %s, uuid is %s", adKey, getSiteUuid());
        final String uuid = (adKey != null) ? adKey : getSiteUuid();
        return uuid;
    }

    /**
     * Get the UUID for this user.
     */
    //TODO: docs about where we get this UUID from and how.
    private String getSiteUuid() {
        String uuid = "";
        try {
            uuid = settings.getString(UUID_KEY, "");
            if (uuid.equals("")) {
                uuid = generateSiteUuid();
            }
        } catch (Exception ex) {
            PLog("Exception caught during site uuid generation: %s", ex.toString());
        }
        return uuid;
    }

    /**
     * Read the Parsely UUID from application context or make a new one.
     *
     * @return The UUID to use for this user.
     */
    private String generateSiteUuid() {
        String uuid = Settings.Secure.getString(context.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        PLog(String.format("Generated UUID: %s", uuid));
        return uuid;
    }
    /**
     * Async task to get adKey for this device.
     */
    private class GetAdKey extends AsyncTask<Void, Void, String> {
        private final Context mContext;

        public GetAdKey(Context context) {
            mContext = context;
        }

        @Override
        protected String doInBackground(Void... params) {
            AdvertisingIdClient.Info idInfo = null;
            String advertId = null;
            try {
                idInfo = AdvertisingIdClient.getAdvertisingIdInfo(mContext);
            } catch (GooglePlayServicesRepairableException | IOException |
                     GooglePlayServicesNotAvailableException | IllegalArgumentException e) {
                PLog("No Google play services or error! falling back to device uuid");
                // fall back to device uuid on google play errors
                advertId = getSiteUuid();
            }
            try {
                advertId = idInfo.getId();
            } catch (NullPointerException e) {
                advertId = getSiteUuid();
            }
            return advertId;
        }

        @Override
        protected void onPostExecute(String advertId) {
            adKey = advertId;
        }
    }
}
