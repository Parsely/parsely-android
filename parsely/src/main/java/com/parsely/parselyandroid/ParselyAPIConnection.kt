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
package com.parsely.parselyandroid

import android.os.AsyncTask
import java.net.HttpURLConnection
import java.net.URL

internal class ParselyAPIConnection(private val tracker: ParselyTracker) : AsyncTask<String?, Exception?, Void?>() {
    private var exception: Exception? = null
    protected override fun doInBackground(vararg data: String?): Void? {
        var connection: HttpURLConnection? = null
        try {
            if (data.size == 1) {  // non-batched (since no post data is included)
                connection = URL(data[0]).openConnection() as HttpURLConnection
                connection.inputStream
            } else if (data.size == 2) {  // batched (post data included)
                connection = URL(data[0]).openConnection() as HttpURLConnection
                connection.doOutput = true // Triggers POST (aka silliest interface ever)
                connection.setRequestProperty("Content-Type", "application/json")
                val output = connection.outputStream
                output.write(data[1]?.toByteArray())
                output.close()
                connection.inputStream
            }
        } catch (ex: Exception) {
            exception = ex
        }
        return null
    }

    override fun onPostExecute(result: Void?) {
        if (exception != null) {
            ParselyTracker.PLog("Pixel request exception")
            ParselyTracker.PLog(exception.toString())
        } else {
            ParselyTracker.PLog("Pixel request success")

            // only purge the queue if the request was successful
            tracker.purgeEventsQueue()
            ParselyTracker.PLog("Event queue empty, flush timer cleared.")
            tracker.stopFlushTimer()
        }
    }
}
