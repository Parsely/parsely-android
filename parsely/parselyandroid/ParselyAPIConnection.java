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

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import android.os.AsyncTask;

public class ParselyAPIConnection extends AsyncTask<String, Exception, URLConnection> {

    public Exception exception;

    @Override
    protected URLConnection doInBackground(String... data) {
        URLConnection connection = null;
        try{
            if(data.length == 1){  // non-batched (since no post data is included)
                connection = new URL(data[0]).openConnection();
                connection.getInputStream();
            } else if(data.length == 2){  // batched (post data included)
                connection = new URL(data[0]).openConnection();
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
                output.close();
                connection.getInputStream();
            }

        } catch (Exception ex){
            this.exception = ex;
            return null;
        }
        return connection;
    }

    protected void onPostExecute(URLConnection conn){
        if(this.exception != null){
            ParselyTracker.PLog("Pixel request exception");
            ParselyTracker.PLog(this.exception.toString());
        } else {
            ParselyTracker.PLog("Pixel request success");

            ParselyTracker instance = null;
            try {
                instance = ParselyTracker.sharedInstance();
            } catch (NullPointerException ex) {
                ParselyTracker.PLog("ParselyTracker is null");
            }

            if (instance != null) {
                // only purge the queue if the request was successful
                instance.eventQueue.clear();
                instance.purgeStoredQueue();

                if(instance.queueSize() == 0 && instance.storedEventsCount() == 0){
                    ParselyTracker.PLog("Event queue empty, flush timer cleared.");
                    instance.stopFlushTimer();
                }
            }
        }
    }
}
