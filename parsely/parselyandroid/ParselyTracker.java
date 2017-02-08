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

import java.io.EOFException;
import java.util.Calendar;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.StringWriter;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings.Secure;

import org.codehaus.jackson.map.ObjectMapper;

/*! \brief Tracks Parse.ly app views in Android apps
*
*  Accessed as a singleton. Maintains a queue of pageview events in memory and periodically
*  flushes the queue to the Parse.ly pixel proxy server.
*/
public class ParselyTracker {
    private static ParselyTracker instance = null;
    private static int DEFAULT_FLUSH_INTERVAL = 60;
    private static String DEFAULT_URLREF = "parsely_mobile_sdk";

    /*! \brief types of post identifiers
    *
    *  Representation of the allowed post identifier types
    */
    private enum kIdType{ kUrl, kPostId }

    private String apikey, rootUrl, storageKey, uuidkey, urlref;
    private SharedPreferences settings;
    private int queueSizeLimit, storageSizeLimit;
    public int flushInterval;
    protected ArrayList<Map<String, Object>> eventQueue;
    private Map<kIdType, String> idNameMap;
    private Map<String, String> deviceInfo;
    private Context context;
    private Timer timer;

    /*! \brief Register a pageview event using a canonical URL
    *
    *  @param url The canonical URL of the article being tracked
    *  (eg: "http://samplesite.com/some-old/article.html")
    */
    public void trackURL(String url){
        this.track(url, kIdType.kUrl);
    }

    /*! \brief Register a pageview event using a CMS post identifier
    *
    *  @param pid A string uniquely identifying this post. This **must** be unique within Parsely's
    *  database.
    */
    public void trackPostId(String pid){
        this.track(pid, kIdType.kPostId);
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
    private void track(String identifier, kIdType idType){
        PLog("Track called for %s", identifier);

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        double timestamp = calendar.getTimeInMillis() / 1000.0;

        Map<String, Object> params = new HashMap<>();
        params.put(this.idNameMap.get(idType), identifier);
        params.put("ts", timestamp);
        params.put("data", this.deviceInfo);
        this.eventQueue.add(params);
        PLog("%s", params);
        new QueueManager().execute();
        if(this.timer == null){
            this.setFlushTimer();
            PLog("Flush timer set to %d", this.flushInterval);
        }
    }

    public void flush() {
        // needed for call from MainActivity
        new FlushQueue().execute();
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
    private void sendBatchRequest(ArrayList<Map<String, Object>> queue){
        PLog("Sending batched request of size %d", queue.size());
        if (queue == null || queue.size() == 0) {
            return;
        }
        Map<String, Object> batchMap = new HashMap<>();

        // the object contains only one copy of the queue's invariant data
        batchMap.put("data", queue.get(0).get("data"));
        ArrayList<Map<String, Object>> events = new ArrayList<>();

        for(Map<String, Object> event : queue){
            String field = null, value = null;
            if(event.get("url") != null){
                field = "url";
                value = (String)event.get("url");
            } else if(event.get("postid") != null){
                field = "postid";
                value = (String)event.get("postid");
            }

            Map<String, Object> _toAdd = new HashMap<>();
            _toAdd.put(field, value);
            _toAdd.put("ts", String.format("%f", (double)event.get("ts")));
            events.add(_toAdd);
        }
        batchMap.put("events", events);

        PLog("Setting API connection");
        new ParselyAPIConnection().execute(this.rootUrl + "mobileproxy", this.JsonEncode(batchMap));
        PLog("Requested %s", this.rootUrl);
        PLog("Data %s", this.JsonEncode(batchMap));
    }

    private boolean isReachable(){
        ConnectivityManager cm = (ConnectivityManager)this.context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private void persistQueue(){
        PLog("Persisting event queue");
        ArrayList<Map<String, Object>> storedQueue = this.getStoredQueue();
        if (storedQueue == null) {
            storedQueue = new ArrayList<>();
        }
        HashSet<Map<String, Object>> hs = new HashSet<>();
        hs.addAll(storedQueue);
        hs.addAll(this.eventQueue);
        storedQueue.clear();
        storedQueue.addAll(hs);
        this.persistObject(storedQueue);
    }

    private ArrayList<Map<String, Object>> getStoredQueue() {
        ArrayList<Map<String, Object>> storedQueue = new ArrayList<>();
        try{
            FileInputStream fis = this.context.getApplicationContext().openFileInput(
                    this.storageKey);
        ObjectInputStream ois = new ObjectInputStream(fis);
        //noinspection unchecked
        storedQueue = (ArrayList<Map<String, Object>>)ois.readObject();
        ois.close();
        } catch(EOFException ex){
            PLog("");
        }
        catch(Exception ex){
            PLog("Exception thrown during queue deserialization: %s", ex.toString());
        }
        assert storedQueue != null;
        return storedQueue;
    }

    protected void purgeStoredQueue(){
        this.persistObject(null);
    }

    private void expelStoredEvent(){
        ArrayList<Map<String, Object>> storedQueue = this.getStoredQueue();
        storedQueue.remove(0);
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private void persistObject(Object o){
        try{
            FileOutputStream fos = this.context.getApplicationContext().openFileOutput(
                                       this.storageKey,
                                       android.content.Context.MODE_PRIVATE
                                   );
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(o);
            oos.close();
        } catch (Exception ex){
            PLog("Exception thrown during queue serialization: %s", ex.toString());
        }
    }

    private String JsonEncode(Map<String, Object> map){
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

    /*! \brief Allow Parsely to send pageview events
    *
    *  Instantiates the callback timer responsible for flushing the events queue.
    *  Can be called before of after `stop`, but has no effect if used before instantiating the
    *  singleton
    */
    public void setFlushTimer(){
        if(this.flushTimerIsActive()){
            this.stopFlushTimer();
        }
        this.timer = new Timer();
        this.timer.scheduleAtFixedRate(new TimerTask(){
            public void run(){
                flush();
            }
        }, this.flushInterval * 1000, this.flushInterval * 1000);
    }

    /*! \brief Is the callback timer running
    *
    *  @return `true` if the callback timer is currently running, `false` otherwise
    */
    public boolean flushTimerIsActive(){
        return this.timer != null;
    }

    /*! \brief Disallow Parsely from sending pageview events
    *
    *  Invalidates the callback timer responsible for flushing the events queue.
    *  Can be called before or after `start`, but has no effect if used before instantiating the
    *  singleton
    */
    public void stopFlushTimer(){
        if(this.timer != null){
            this.timer.cancel();
            this.timer.purge();
        }
        this.timer = null;
    }

    private String generateSiteUuid(){
        String uuid = Secure.getString(this.context.getApplicationContext().getContentResolver(),
                Secure.ANDROID_ID);
        PLog(String.format("Generated UUID: %s", uuid));
        return uuid;
    }

    private String getSiteUuid(){
        String uuid = "";
        try{
            uuid = this.settings.getString(this.uuidkey, "");
            if(uuid.equals("")){
                uuid = this.generateSiteUuid();
            }
        } catch(Exception ex){
            PLog("Exception caught during site uuid generation: %s", ex.toString());
        }
        return uuid;
    }

    private Map<String, String> collectDeviceInfo(){
        Map<String, String> dInfo = new HashMap<>();

        dInfo.put("parsely_site_uuid", this.getSiteUuid());
        dInfo.put("idsite", this.apikey);
        dInfo.put("manufacturer", android.os.Build.MANUFACTURER);
        dInfo.put("os", "android");
        dInfo.put("urlref", this.urlref);
        dInfo.put("os_version", String.format("%d", android.os.Build.VERSION.SDK_INT));

        Resources appR = this.context.getApplicationContext().getResources();
        CharSequence txt = appR.getText(appR.getIdentifier("app_name","string",
                this.context.getApplicationContext().getPackageName()));
        dInfo.put("appname", txt.toString());

        return dInfo;
    }

    protected ParselyTracker(String apikey, int flushInterval, String urlref, Context c){
        this.context = c;
        this.settings = this.context.getSharedPreferences("parsely-prefs", 0);

        this.apikey = apikey;
        this.uuidkey = "parsely-uuid";
        this.flushInterval = flushInterval;
        this.storageKey = "parsely-events.ser";
        //this.rootUrl = "http://10.0.2.2:5001/";  // emulator localhost
        this.rootUrl = "http://srv.pixel.parsely.com/";
        this.urlref = urlref;
        this.queueSizeLimit = 50;
        this.storageSizeLimit = 100;
        this.deviceInfo = this.collectDeviceInfo();

        this.eventQueue = new ArrayList<>();

        // set up a map of enumerated type to identifier name
        this.idNameMap = new HashMap<>();
        this.idNameMap.put(kIdType.kUrl, "url");
        this.idNameMap.put(kIdType.kPostId, "postid");

        if(this.getStoredQueue() != null && this.getStoredQueue().size() > 0){
            this.setFlushTimer();
        }
    }

    /*! \brief Singleton instance accessor. Note: This must be called after
    sharedInstance(String, Context)
    *
    *  @return The singleton instance
    */
    public static ParselyTracker sharedInstance(){
        if(instance == null){
            return null;
        }
        return instance;
    }

    /*! \brief Singleton instance factory Note: this must be called before `sharedInstance()`
    *
    *  @param apikey The Parsely public API key (eg "samplesite.com")
    *  @param c The current Android application context
    *  @return The singleton instance
    */
    public static ParselyTracker sharedInstance(String apikey, Context c){
        return ParselyTracker.sharedInstance(apikey, DEFAULT_FLUSH_INTERVAL, DEFAULT_URLREF, c);
    }

    /*! \brief Singleton instance factory Note: this must be called before `sharedInstance()`
    *
    *  @param apikey The Parsely public API key (eg "samplesite.com")
    *  @param flushInterval The interval at which the events queue should flush, in seconds
    *  @param c The current Android application context
    *  @return The singleton instance
    */
    public static ParselyTracker sharedInstance(String apikey, int flushInterval, Context c){
        if(instance == null){
            instance = new ParselyTracker(apikey, flushInterval, DEFAULT_URLREF, c);
        }
        return instance;
    }

    /*! \brief Singleton instance factory Note: this must be called before `sharedInstance()`
    *
    *  @param apikey The Parsely public API key (eg "samplesite.com")
    *  @param flushInterval The interval at which the events queue should flush, in seconds
    *  @param urlref The referrer string to send with pixel requests
    *  @param c The current Android application context
    *  @return The singleton instance
    */
    public static ParselyTracker sharedInstance(String apikey, int flushInterval, String urlref, Context c){
        if(instance == null){
            instance = new ParselyTracker(apikey, flushInterval, urlref, c);
        }
        return instance;
    }

    public int queueSize(){ return this.eventQueue.size(); }
    public int storedEventsCount(){
        ArrayList<Map<String, Object>> ar = this.getStoredQueue();
        if(ar != null){
            return ar.size();
        }
        return 0;
    }

    protected static void PLog(String logstring, Object... objects){
        if (logstring.equals("")) {
            return;
        }
        System.out.println(new Formatter().format("[Parsely] " + logstring, objects).toString());
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    public class QueueManager extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            ArrayList<Map<String, Object>> storedQueue = getStoredQueue();
            // if event queue is too big, push to persisted storage
            if (eventQueue.size() >= queueSizeLimit + 1) {
                PLog("Queue size exceeded, expelling oldest event to persistent memory");
                persistQueue();
                eventQueue.remove(0);
                // if persisted storage is too big, expel one
                if (storedQueue != null) {
                    if (storedEventsCount() > storageSizeLimit) {
                        expelStoredEvent();
                    }
                }
            }
            return null;
        }
    }

    public class FlushQueue extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            ArrayList<Map<String, Object>> storedQueue = getStoredQueue();
            PLog("%d events in queue, %d stored events", eventQueue.size(), storedEventsCount());
            // in case both queues have been flushed and app quits, don't crash
            if ((eventQueue == null || eventQueue.size() == 0) &&
                (storedQueue == null || storedQueue.size() == 0))
            {
                stopFlushTimer();
                return null;
            }
            if(!isReachable()){
                PLog("Network unreachable. Not flushing.");
                return null;
            }
            HashSet<Map<String, Object>> hs = new HashSet<>();
            ArrayList<Map<String, Object>> newQueue = new ArrayList<>();

            hs.addAll(eventQueue);
            if(storedQueue != null){
                hs.addAll(storedQueue);
            }
            newQueue.addAll(hs);
            PLog("Flushing queue");
            sendBatchRequest(newQueue);
            return null;
        }
    }
}
