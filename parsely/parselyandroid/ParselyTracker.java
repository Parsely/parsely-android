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

package com.parsely.parselyandroid;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.util.Log;

import org.codehaus.jackson.map.ObjectMapper;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingFormatArgumentException;
import java.util.TimeZone;

/**
 * Tracks Parse.ly app views in Android apps
 * Maintains a queue of pageview events on disk and requires periodic manual flushing by the implementing developer
 * as javadoced in the {@LINK #flush} method.
 */
public class ParselyTracker {
    public static String DEFAULT_URLREF = "parsely_mobile_sdk";

    /*! \brief types of post identifiers
    *
    *  Representation of the allowed post identifier types
    */
    private enum kIdType {
        kUrl, kPostId
    }

    private static boolean DEBUG = true;
    private static String apikey, rootUrl, storageKey, uuidkey, urlref;
    private SharedPreferences settings;
    private Map<kIdType, String> idNameMap;
    private Map<String, String> deviceInfo;
    private Context context;

    /**
     * @param apikey  The API key for the Parsely server
     * @param urlref  {@link #DEFAULT_URLREF}, can use default or change as required
     * @param context
     */
    public ParselyTracker(String apikey, String urlref, Context context) {
        this.context = context.getApplicationContext();
        this.settings = this.context.getSharedPreferences("parsely-prefs", 0);
        this.apikey = apikey;
        this.uuidkey = "parsely-uuid";
        this.storageKey = "parsely-events.ser";
        //this.rootUrl = "http://10.0.2.2:5001/";  // emulator localhost
        this.rootUrl = "http://srv.pixel.parsely.com/";
        this.urlref = urlref;
        this.deviceInfo = this.collectDeviceInfo();
        // set up a map of enumerated type to identifier name
        this.idNameMap = new HashMap<>();
        this.idNameMap.put(kIdType.kUrl, "url");
        this.idNameMap.put(kIdType.kPostId, "postid");
    }

    /*! \brief Register a pageview event using a canonical URL
    *
    *  @param url The canonical URL of the article being tracked
    *  (eg: "http://samplesite.com/some-old/article.html")
    */
    public void trackURL(String url) {
        this.track(url, kIdType.kUrl);
    }

    /*! \brief Register a pageview event using a CMS post identifier
    *
    *  @param pid A string uniquely identifying this post. This **must** be unique within Parsely's
    *  database.
    */
    public void trackPostId(String pid) {
        this.track(pid, kIdType.kPostId);
    }

    /**
     * It's recommended to flush the queue in an onPause callback of the main activity.
     * onPause is highly likely to get called, but you can also use onStop if desired.
     * Since the queue is persisted to disk technically you can flush the queue whenever you'd like.
     */
    public void flush() {
        // needed for call from MainActivity
        new FlushQueue(context).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    /*! \brief Register a pageview event
    *
    *  Places a data structure representing the event into the in-memory queue for later use
    *
    *  **Note**: Events placed into this queue will be discarded if the size of the persistent queue
    *  store exceeds `storageSizeLimit`.
    *
    *  @param identifier The post id or canonical URL uniquely identifying the post
    *  @param idType enum element indicating what type of identifier the first argument is
    */
    private void track(String identifier, kIdType idType) {
        PLog("Track called for %s", identifier);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        double timestamp = calendar.getTimeInMillis() / 1000.0;
        Map<String, Object> params = new HashMap<>();
        params.put(this.idNameMap.get(idType), identifier);
        params.put("ts", timestamp);
        params.put("data", this.deviceInfo);
        PLog("%s", params);
        new QueueManager(context).executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, params);
    }

    private static boolean isReachable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private static ArrayList<Map<String, Object>> getStoredQueue(Context context) {
        //Cannot write to disk on the main thread
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            throw new WrongThreadException();
        }
        ArrayList<Map<String, Object>> storedQueue = new ArrayList<>();
        try {
            FileInputStream fis = context.openFileInput(storageKey);
            ObjectInputStream ois = new ObjectInputStream(fis);
            //noinspection unchecked
            storedQueue = (ArrayList<Map<String, Object>>) ois.readObject();
            ois.close();
        } catch (EOFException ex) {
            PLog("");
        } catch (Exception ex) {
            PLog("Exception thrown during queue deserialization: %s", ex.toString());
        }
        assert storedQueue != null;
        return storedQueue;
    }

    private static void purgeStoredQueue(Context context) {
        persistObject(context, null);
    }

    private static String jsonEncode(Map<String, Object> map) {
        ObjectMapper mapper = new ObjectMapper();
        String ret = null;
        try {
            StringWriter strWriter = new StringWriter();
            mapper.writeValue(strWriter, map);
            ret = strWriter.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private String generateSiteUuid() {
        String uuid = Secure.getString(this.context.getApplicationContext().getContentResolver(), Secure.ANDROID_ID);
        PLog(String.format("Generated UUID: %s", uuid));
        return uuid;
    }

    private String getSiteUuid() {
        String uuid = "";
        try {
            uuid = this.settings.getString(this.uuidkey, "");
            if (uuid.equals("")) {
                uuid = this.generateSiteUuid();
            }
        } catch (Exception ex) {
            PLog("Exception caught during site uuid generation: %s", ex.toString());
        }
        return uuid;
    }

    private Map<String, String> collectDeviceInfo() {
        Map<String, String> dInfo = new HashMap<>();
        dInfo.put("parsely_site_uuid", this.getSiteUuid());
        dInfo.put("idsite", this.apikey);
        dInfo.put("manufacturer", android.os.Build.MANUFACTURER);
        dInfo.put("os", "android");
        dInfo.put("urlref", this.urlref);
        dInfo.put("os_version", String.format("%d", android.os.Build.VERSION.SDK_INT));
        Resources appR = this.context.getApplicationContext().getResources();
        CharSequence txt = appR.getText(appR.getIdentifier("app_name", "string", this.context.getApplicationContext().getPackageName()));
        dInfo.put("appname", txt.toString());
        return dInfo;
    }

    private static int storedEventsCount(Context context) {
        ArrayList<Map<String, Object>> ar = getStoredQueue(context);
        if (ar != null) {
            return ar.size();
        }
        return 0;
    }

    private static void PLog(String logstring, Object... objects) {
        if (logstring.equals("")) {
            return;
        }
        if (!DEBUG) {
            return;
        }
        try {
            Log.d(ParselyTracker.class.getSimpleName(), new Formatter().format("[Parsely] " + logstring, objects).toString());
        } catch (MissingFormatArgumentException e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private static void persistObject(Context context, Object o) {
        //Cannot write to disk on the main thread
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            throw new WrongThreadException();
        }
        try {
            FileOutputStream fos = context.openFileOutput(storageKey, android.content.Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(o);
            oos.close();
        } catch (Exception ex) {
            PLog("Exception thrown during queue serialization: %s", ex.toString());
        }
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private static class QueueManager extends AsyncTask<Map<String, Object>, Void, Void> {
        private Context context;

        public QueueManager(Context context) {
            this.context = context;
        }

        @Override
        protected Void doInBackground(Map<String, Object>... params) {
            PLog("Persisting event queue");
            ArrayList<Map<String, Object>> storedQueue = getStoredQueue(context);
            if (storedQueue == null) {
                storedQueue = new ArrayList<>();
            }
            storedQueue.add(params[0]);
            persistObject(context, storedQueue);
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private static class FlushQueue extends AsyncTask<Void, Void, Void> {
        private Context context;

        public FlushQueue(Context context) {
            this.context = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            ArrayList<Map<String, Object>> storedQueue = getStoredQueue(context);
            PLog("%d stored events", storedEventsCount(context));
            // in case both queues have been flushed and app quits, don't crash
            if (storedQueue == null) {
                return null;
            }
            if (storedQueue.size() == 0) {
                return null;
            }
            if (!isReachable(context)) {
                PLog("Network unreachable. Not flushing.");
                return null;
            }
            PLog("Flushing queue");
            sendBatchRequest(storedQueue);
            return null;
        }

        /*!  \brief Generate pixel requests from the queue
        *
        *  Empties the entire queue and sends the appropriate pixel requests.
        *  Called automatically after a number of seconds determined by `flushInterval`.
        */

        /*!  \brief Send the entire queue as a single request
        *
        *   Creates a large POST request containing the JSON encoding of the entire queue.
        *   Sends this request to the proxy server, which forwards requests to the pixel server.
        *
        *   @param queue The list of event dictionaries to serialize
        */
        private void sendBatchRequest(ArrayList<Map<String, Object>> queue) {
            PLog("Sending batched request of size %d", queue.size());
            Map<String, Object> batchMap = new HashMap<>();
            // the object contains only one copy of the queue's invariant data
            batchMap.put("data", queue.get(0).get("data"));
            ArrayList<Map<String, Object>> events = new ArrayList<>();
            for (Map<String, Object> event : queue) {
                String field = null, value = null;
                if (event.get("url") != null) {
                    field = "url";
                    value = (String) event.get("url");
                } else if (event.get("postid") != null) {
                    field = "postid";
                    value = (String) event.get("postid");
                }

                Map<String, Object> _toAdd = new HashMap<>();
                _toAdd.put(field, value);
                _toAdd.put("ts", String.format("%f", (double) event.get("ts")));
                events.add(_toAdd);
            }
            batchMap.put("events", events);
            PLog("Setting API connection");
            String jsonEncodedBatch = jsonEncode(batchMap);
            parselyPost(rootUrl + "mobileproxy", jsonEncodedBatch);
            PLog("Requested %s", rootUrl);
            PLog("Data %s", jsonEncodedBatch);
        }

        private void parselyPost(String... data) {
            try {
                URLConnection connection = new URL(data[0]).openConnection();
                if (data.length == 2) {  // batched (post data included)
                    connection.setDoOutput(true);  // Triggers POST (aka silliest interface ever)
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    OutputStream output = connection.getOutputStream();
                    String query = "";
                    try {
                        query = String.format("rqs=%s", URLEncoder.encode(data[1], "UTF-8"));
                    } catch (UnsupportedEncodingException ex) {
                        ParselyTracker.PLog("");
                    }
                    output.write(query.getBytes());
                    output.flush();
                    output.close();
                }
                ParselyTracker.PLog("Pixel request success");
                purgeStoredQueue(context);

            } catch (Exception ex) {
                ParselyTracker.PLog("Pixel request exception");
                ParselyTracker.PLog(ex.toString());
            }
        }
    }

    private static class WrongThreadException extends RuntimeException {

    }
}
