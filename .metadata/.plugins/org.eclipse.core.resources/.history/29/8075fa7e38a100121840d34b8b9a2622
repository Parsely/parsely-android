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

package com.example.parselyandroid;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class ParselyTracker {
	private static ParselyTracker instance = null;
	
	enum kIdType{ kUrl, kPostId }
	
	private String apikey, rootUrl;
	private int flushInterval, queueSizeLimit, storageSizeLimit;
	private Boolean shouldBatchRequests;
	private ArrayList<Map> eventQueue;
	private Map<kIdType, String> idNameMap;
	private Timer timer; 
	
	public void track(String identifier, kIdType idType){
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
	
	public void flush(){
		PLog("Flushing queue");
	}
	
	private int queueSize(){
		return this.eventQueue.size();
	}
	
	private void persistQueue(){
		PLog("ERROR persistQueue NOT IMPLEMENTED!!!");
	}
	
	private int storedEventsCount(){
		PLog("ERROR storedEventsCount NOT IMPLEMENTED!!!");
		return 0;
	}
	
	private boolean getStoredQueue(){
		PLog("ERROR getStoredQueue NOT IMPLEMENTED!!!");
		return false;
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
		
		this.eventQueue = new ArrayList<Map>();
		
		this.idNameMap = new HashMap<kIdType, String>();
		this.idNameMap.put(kIdType.kUrl, "url");
		this.idNameMap.put(kIdType.kPostId, "postid");
		
		if(this.getStoredQueue()){
			this.setFlushTimer();
		}
	}
	
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