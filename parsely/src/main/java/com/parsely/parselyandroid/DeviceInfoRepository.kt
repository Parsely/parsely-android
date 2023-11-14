package com.parsely.parselyandroid;

import static com.parsely.parselyandroid.ParselyTracker.PLog;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class DeviceInfoRepository {

    private static final String UUID_KEY = "parsely-uuid";
    private String adKey = null;
    @NonNull
    private final Context context;
    private final SharedPreferences settings;

    DeviceInfoRepository(@NonNull Context context) {
        this.context = context;
        settings = context.getSharedPreferences("parsely-prefs", 0);
        new GetAdKey(context).execute();
    }

    /**
     * Collect device-specific info.
     * <p>
     * Collects info about the device and user to use in Parsely events.
     */
    Map<String, String> collectDeviceInfo() {
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
