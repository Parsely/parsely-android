//
// ParselyTracker.java
// ParselyAndroid
//
// Copyright 2013 Parse.ly
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.parsely.parselyandroid;

import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.net.URL;
import java.util.Random;

import android.content.SharedPreferences;
import android.content.Context;
import android.content.Context.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

/*! \brief Manages pageview events and analytics data for Parsely on Android
*
*  Accessed as a singleton. Maintains a queue of pageview events in memory and periodically
*  flushes the queue to the Parsely pixel proxy server.
*/ 
public class ParselyTracker {
    private static ParselyTracker instance = null;

    /*! \brief types of post identifiers
    *
    *  Representation of the allowed post identifier types
    */
    private static enum kIdType{ kUrl, kPostId }

    private String apikey, rootUrl, storageKey, uuidkey;
    private SharedPreferences settings;
    private int flushInterval, queueSizeLimit, storageSizeLimit;
    private Boolean shouldBatchRequests;
    private ArrayList<Map<String, Object>> eventQueue;
    private Map<kIdType, String> idNameMap;
    private Map<String, String> deviceInfo;
    private Context context;
    private Timer timer;

    /*! \brief Register a pageview event using a canonical URL
    *
    *  @param url The canonical URL of the article being tracked (eg: "http://samplesite.com/some-old/article.html")
    */
    public void trackURL(String url){
        this.track(url, kIdType.kUrl);
    }

    /*! \brief Register a pageview event using a CMS post identifier
    *
    *  @param pid A string uniquely identifying this post. This **must** be unique within Parsely's database.
    */
    public void trackPostId(String pid){
        this.track(pid, kIdType.kPostId);
    }

    /*! \brief Registers a pageview event
    *
    *  Places a data structure representing the event into the in-memory queue for later use
    *
    *  **Note**: Events placed into this queue will be discarded if the size of the persistent queue store exceeds `storageSizeLimit`.
    *  
    *  @param identifier The post id or canonical URL uniquely identifying the post
    *  @param idType enum element indicating what type of identifier the first argument is
    */
    private void track(String identifier, kIdType idType){
        PLog(String.format("Track called for %s", identifier));
        
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        long timestamp = calendar.getTimeInMillis() / 1000L;

        Map<String, Object> params = new HashMap<String, Object>();
        params.put(this.idNameMap.get(idType), identifier);
        params.put("ts", timestamp);
        params.put("data", this.deviceInfo);

        this.eventQueue.add(params);

        PLog(String.format("%s", params));

        if(this.queueSize() >= this.queueSizeLimit + 1){
            PLog("Queue size exceeded, expelling oldest event to persistent memory");
            this.persistQueue();
            this.eventQueue.remove(0);
        }

        if(this.storedEventsCount() > this.storageSizeLimit){
            this.expelStoredEvent();
        }

        if(this.timer == null){
            this.setFlushTimer();
            PLog(String.format("Flush timer set to %d", this.flushInterval));
        }
    }

    /*!  \brief Generate pixel requests from the queue
    *
    *  Empties the entire queue and sends the appropriate pixel requests.
    *  If `shouldBatchRequests` is true, the queue is sent as a minimum number of requests.
    *  Called automatically after a number of seconds determined by `flushInterval`.
    */
    public void flush(){
        PLog(String.format("%d events in queue, %d stored events", this.queueSize(), this.storedEventsCount()));

        if(this.eventQueue.size() == 0 && this.getStoredQueue().size() == 0){
            this.stopFlushTimer();
            return;
        }

        if(!this.isReachable()){
            PLog("Network unreachable. Not flushing.");
            return;
        }

        ArrayList<Map<String, Object>> storedQueue = this.getStoredQueue();
        ArrayList<Map<String, Object>> newQueue = (ArrayList<Map<String, Object>>)this.eventQueue.clone();
        if(storedQueue != null){
            newQueue.addAll(storedQueue);
        }

        PLog("Flushing queue...");
        if(this.shouldBatchRequests){
            this.sendBatchRequest(newQueue);
        } else {
            for(Map<String, Object> event : newQueue){
                this.flushEvent(event);
            }
        }
        PLog("done");

        this.eventQueue.clear();
        this.purgeStoredQueue();
        
        if(this.queueSize() == 0 && this.storedEventsCount() == 0){
            PLog("Event queue empty, flush timer cleared.");
            this.stopFlushTimer();
        }
    }

    /*! \brief Send a single pixel request
    *
    *  Sends a single request directly to Parsely's pixel server, bypassing the proxy.
    *  Prefer `sendBatchRequest:` to this method, as `sendBatchRequest:` causes less battery usage
    *
    *  @param event A dictionary containing data for a single pageview event
    */
    private void flushEvent(Map<String, Object> event){
        PLog(String.format("flushing individual event %s", event));
        
        // add the timestamp to the data object for non-batched requests, since they are sent directly to the pixel server
        Map<String, Object> data = (Map<String, Object>)event.get("data");
        data.put("ts", event.get("ts"));
        
        Random gen = new Random();
        
        String url = String.format("%s?rand=%lli&idsite=%s&url=%s&urlref=%s&data=%s",
                         this.rootUrl,
                         1000000000 + gen.nextInt() % 999999999,
                         this.apikey,
                         URLEncoder.encode((String)event.get("url")),
                         "mobile",  // urlref
                         URLEncoder.encode(this.JsonEncode(data))
                     );

        this.APIConnection(url);
        PLog(String.format("Requested %s", url));
        PLog(String.format("Data %s", this.JsonEncode(data)));
    }

    /*!  \brief Send the entire queue as a single request
    *
    *   Creates a large POST request containing the JSON encoding of the entire queue.
    *   Sends this request to the proxy server, which forwards requests to the pixel server.
    *
    *   @param queue The list of event dictionaries to serialize
    */
    private void sendBatchRequest(ArrayList<Map<String, Object>> queue){
        PLog("Sending batched request");
        
        Map<String, Object> batchMap = new HashMap<String, Object>();
        
        // the object contains only one copy of the queue's invariant data
        batchMap.put("data", queue.get(0).get("data"));
        ArrayList<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
       
        for(Iterator<Map<String, Object>> i = queue.iterator(); i.hasNext();){
            Map<String, Object> event = (Map<String, Object>)i.next();
            String field = null, value = null;
            if(event.get("url") != null){
                field = "url";
                value = (String)event.get("url");
            } else if(event.get("postid") != null){
                field = "postid";
                value = (String)event.get("postid");
            }
            
            Map<String, Object> _toAdd = new HashMap<String, Object>();
            _toAdd.put(field, value);
            _toAdd.put("ts", event.get("ts"));
            events.add(_toAdd);
        }
        batchMap.put("events", events);
        
        this.APIConnection(this.rootUrl, this.JsonEncode(batchMap));
        PLog(String.format("Requested %s", this.rootUrl));
        PLog(String.format("Data %s", this.JsonEncode(batchMap)));
    }

    private boolean isReachable(){
        ConnectivityManager cm = (ConnectivityManager)this.context.getSystemService(Context.CONNECTIVITY_SERVICE);    
        NetworkInfo netInfo = cm.getActiveNetworkInfo();    
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private void persistQueue(){
        PLog("Persisting event queue");
        ArrayList<Map<String, Object>> storedQueue = this.getStoredQueue();
        storedQueue.addAll(this.eventQueue);
        this.persistObject(storedQueue);
    }

    private ArrayList<Map<String, Object>> getStoredQueue(){
        ArrayList<Map<String, Object>> storedQueue = new ArrayList<Map<String, Object>>();
        try{
            FileInputStream fis = this.context.getApplicationContext().openFileInput(
                    this.storageKey
                    );
            ObjectInputStream ois = new ObjectInputStream(fis);
            storedQueue = (ArrayList<Map<String, Object>>)ois.readObject();
            ois.close();
        } catch (Exception ex){
            PLog(String.format("Exception thrown during queue deserialization: %s", ex.toString()));
        }
        assert storedQueue != null;
        return storedQueue;
    }

    private void purgeStoredQueue(){
        this.persistObject(null);
    }

    private void expelStoredEvent(){
        ArrayList<Map<String, Object>> storedQueue = this.getStoredQueue();
        storedQueue.remove(0);
    }
    
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
            PLog(String.format("Exception thrown during queue serialization: %s", ex.toString()));
        }
    }
    
    private String JsonEncode(Map<String, Object> map){
        ObjectMapper mapper = new ObjectMapper();
        String ret = null;
        try {
            StringWriter strWriter = new StringWriter();
            mapper.writeValue(strWriter, map);
            ret = strWriter.toString();
          } catch (JsonGenerationException e) {
            e.printStackTrace();
          } catch (JsonMappingException e) {
            e.printStackTrace();
          } catch (IOException e) {
            e.printStackTrace();
          }
        return ret;
    }
    
    private URLConnection APIConnection(String url){
        URLConnection connection = null;
        try{
            connection = new URL(url).openConnection();
            InputStream response = connection.getInputStream();
        } catch (Exception ex){
            PLog("Exception caught during HTTP GET request");
        }
        return connection;
    }
    
    private URLConnection APIConnection(String url, String data){
        URLConnection connection = null;
        try{
            connection = new URL(url).openConnection();
            connection.setDoOutput(true);  // Triggers POST (aka silliest interface ever)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            
            OutputStream output = connection.getOutputStream();
            
            String query = String.format("rqs=%s", URLEncoder.encode(data));
            output.write(query.getBytes());
            output.close();
            
            InputStream response = connection.getInputStream();
        } catch(Exception ex){
            PLog(String.format("Exception caught during HTTP POST request: %s", ex.toString()));
        }
        return connection;
    }

    /*! \brief Allow Parsely to send pageview events
    *
    *  Instantiates the callback timer responsible for flushing the events queue.
    *  Can be called before of after `stop`, but has no effect is used before instantiating the singleton
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
    *  Can be called before or after `start`, but has no effect if used before instantiating the singleton
    */
    public void stopFlushTimer(){
        if(this.timer != null){
            this.timer.cancel();
            this.timer.purge();
        }
        this.timer = null;
    }
    
    private String generateSiteUuid(){
        // Debatable: http://stackoverflow.com/a/2853253/735204
        final TelephonyManager tm = (TelephonyManager) this.context.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);

        final String tmDevice, tmSerial, androidId;
        tmDevice = "" + tm.getDeviceId();
        tmSerial = "" + tm.getSimSerialNumber();
        androidId = "" + android.provider.Settings.Secure.getString(this.context.getApplicationContext().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

        UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
        String uuid = deviceUuid.toString();

        PLog(String.format("Generated UUID: %s", uuid));
        return uuid;
    }
    
    private String getSiteUuid(){
        String uuid = "";
        SharedPreferences.Editor editor = this.settings.edit();
        editor.putString(this.uuidkey, "");
        editor.commit();
        try{
            uuid = this.settings.getString(this.uuidkey, "");
            if(uuid == ""){
                uuid = this.generateSiteUuid();
            }
        } catch(Exception ex){
            PLog(String.format("Exception caught during site uuid generation: %s", ex.toString()));
        }
        return uuid;
    }

    private Map<String, String> collectDeviceInfo(){
        Map<String, String> dInfo = new HashMap<String, String>();
       
        dInfo.put("parsely_site_uuid", this.getSiteUuid());
        dInfo.put("idsite", this.apikey);
        
        return dInfo;
    }

    protected ParselyTracker(String apikey, int flushInterval, Context c){
        this.context = c;
        this.settings = this.context.getSharedPreferences("parsely-prefs", 0);
        
        this.apikey = apikey;
        this.uuidkey = "parsely-uuid";
        this.flushInterval = flushInterval;
        this.storageKey = "parsely-events.ser";
        this.shouldBatchRequests = true;
        this.rootUrl = "http://localhost:5001/mobileproxy";
        this.queueSizeLimit = 5;
        this.storageSizeLimit = 20;
        this.deviceInfo = this.collectDeviceInfo();

        this.eventQueue = new ArrayList<Map<String, Object>>();

        // set up a map of enumerated type to identifier name
        this.idNameMap = new HashMap<kIdType, String>();
        this.idNameMap.put(kIdType.kUrl, "url");
        this.idNameMap.put(kIdType.kPostId, "postid");

        if(this.getStoredQueue() != null){
            this.setFlushTimer();
        }
    }

    /*! \brief Singleton instance accessor. Note: This must be called after sharedInstance(String, Context)
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
        return ParselyTracker.sharedInstance(apikey, 60, c);
    }

    public static ParselyTracker sharedInstance(String apikey, int flushInterval, Context c){
        PLog("In sharedinstance");
        if(instance == null){
            instance = new ParselyTracker(apikey, flushInterval, c);
        }
        return instance;
    }
    
    private int queueSize(){ return this.eventQueue.size(); }
    private int storedEventsCount(){
        ArrayList<Map<String, Object>> ar = (ArrayList<Map<String, Object>>)this.getStoredQueue();
        if(ar != null){
            return ar.size();
        }
        return 0;
    }

    private static void PLog(String logstring){
        System.out.printf("[Parsely] %s\n", logstring);
    }
}