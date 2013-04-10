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

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

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

    private String apikey, rootUrl;
    private int flushInterval, queueSizeLimit, storageSizeLimit;
    private Boolean shouldBatchRequests;
    private ArrayList<Map<String, Object>> eventQueue;
    private Map<kIdType, String> idNameMap;
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
    *  @param postid A string uniquely identifying this post. This **must** be unique within Parsely's database.
    */
    public void trackPostId(String pid){
        this.track(pid, kIdType.kPostId);
    }

    /*! \brief Registers a pageview event
    *
    *  Places a data structure representing the event into the in-memory queue for later use
    *
    *  **Note**: Events placed into this queue will be discarded if the size of the persistent queue store exceeds `storageSizeLimit`.
    */
    private void track(String identifier, kIdType idType){
        PLog(String.format("Track called for %s", identifier));

        Map<String, Object> params = new HashMap<String, Object>();
        params.put(this.idNameMap.get(idType), identifier);
        // add device info, unix timestamp

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
        PLog(String.format("%d events in queue, %d stored events", this.eventQueue.size(), this.getStoredQueue().size()));

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
        
        if(this.eventQueue.size() == 0 && this.getStoredQueue().size() == 0){
            PLog("Event queue empty, flush timer cleared.");
            this.stopFlushTimer();
        }
    }

    private void flushEvent(Map<String, Object> event){
        PLog(String.format("flushing individual event %s", event));
    }

    private void sendBatchRequest(ArrayList<Map<String, Object>> queue){
        PLog("Sending batched request");
    }

    private boolean isReachable(){
        PLog("ERROR isReachable NOT IMPLEMENTED!!!");
        return true;
    }

    private int queueSize(){
        return this.eventQueue.size();
    }

    private void persistQueue(){
        PLog("ERROR persistQueue NOT IMPLEMENTED!!!");
        //http://stackoverflow.com/a/3585036/735204
    }

    private int storedEventsCount(){
        PLog("ERROR storedEventsCount NOT IMPLEMENTED!!!");
        return 0;
    }

    private ArrayList<Map<String, Object>> getStoredQueue(){
        PLog("ERROR getStoredQueue NOT IMPLEMENTED PROPERLY!!!");
        return new ArrayList<Map<String, Object>>();
    }

    private void purgeStoredQueue(){
        PLog("ERROR purgeStoredQueue NOT IMPLEMENTED!!!");
    }

    private void expelStoredEvent(){
        PLog("ERROR expelStoredEvent NOT IMPLEMENTED!!!");
    }

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

    public boolean flushTimerIsActive(){
        return this.timer != null;
    }

    public void stopFlushTimer(){
        if(this.timer != null){
            this.timer.cancel();
            this.timer.purge();
        }
        this.timer = null;
    }

    protected ParselyTracker(String apikey, int flushInterval){
        this.apikey = apikey;
        this.flushInterval = flushInterval;
        this.shouldBatchRequests = true;
        this.rootUrl = "http://localhost:5001/mobileproxy";
        this.queueSizeLimit = 5;
        this.storageSizeLimit = 20;

        this.eventQueue = new ArrayList<Map<String, Object>>();

        // set up a map of enumerated type to identifier name
        this.idNameMap = new HashMap<kIdType, String>();
        this.idNameMap.put(kIdType.kUrl, "url");
        this.idNameMap.put(kIdType.kPostId, "postid");

        if(this.getStoredQueue() != null){
            this.setFlushTimer();
        }
    }

    /*! \brief Singleton instance accessor. Note: This must be called after sharedInstance(String, int)
    *
    *  @return The singleton instance
    */
    public static ParselyTracker sharedInstance(){
        if(instance == null){
            return null;
        }
        return instance;
    }

    public static ParselyTracker sharedInstance(String apikey, int flushInterval){
        PLog("In sharedinstance");
        if(instance == null){
            instance = new ParselyTracker(apikey, flushInterval);
        }
        return instance;
    }

    private static void PLog(String logstring){
        System.out.printf("[Parsely] %s\n", logstring);
    }
}