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

import android.os.AsyncTask;

import androidx.annotation.NonNull;

import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;

class ParselyAPIConnection extends AsyncTask<String, Exception, HttpURLConnection> {

    @NonNull
    private final ParselyTracker tracker;
    private Exception exception;

    ParselyAPIConnection(@NonNull ParselyTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    protected HttpURLConnection doInBackground(String... data) {
        HttpURLConnection connection = null;
        try {
            if (data.length == 1) {  // non-batched (since no post data is included)
                connection = (HttpURLConnection) new URL(data[0]).openConnection();
                connection.getInputStream();
            } else if (data.length == 2) {  // batched (post data included)
                connection = (HttpURLConnection) new URL(data[0]).openConnection();
                connection.setDoOutput(true);  // Triggers POST (aka silliest interface ever)
                connection.setRequestProperty("Content-Type", "application/json");

                OutputStream output = connection.getOutputStream();
                output.write(data[1].getBytes());
                output.close();
                connection.getInputStream();
            }

        } catch (Exception ex) {
            this.exception = ex;
            return null;
        }
        return connection;
    }

    protected void onPostExecute(HttpURLConnection conn) {
        if (this.exception != null) {
            ParselyTracker.PLog("Pixel request exception");
            ParselyTracker.PLog(this.exception.toString());
        } else {
            ParselyTracker.PLog("Pixel request success");

            // only purge the queue if the request was successful
            tracker.eventQueue.clear();
            tracker.purgeStoredQueue();

            ParselyTracker.PLog("Event queue empty, flush timer cleared.");
            tracker.stopFlushTimer();
        }
    }
}
