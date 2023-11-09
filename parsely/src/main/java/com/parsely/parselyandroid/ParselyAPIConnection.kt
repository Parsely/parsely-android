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

import com.parsely.parselyandroid.ParselyTracker.ROOT_URL
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ParselyAPIConnection @JvmOverloads constructor(
    private val url: String,
    private val tracker: ParselyTracker,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private var exception: Exception? = null

    suspend fun send(payload: String) {
        withContext(dispatcher) {
            val connection: HttpURLConnection?
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val output = connection.outputStream
                output.write(payload.toByteArray())
                output.close()
                connection.inputStream
            } catch (ex: Exception) {
                exception = ex
            }

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
}
