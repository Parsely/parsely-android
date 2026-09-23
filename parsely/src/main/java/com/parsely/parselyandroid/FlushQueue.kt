package com.parsely.parselyandroid

import com.parsely.parselyandroid.JsonSerializer.toParselyEventsPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FlushQueue(
    private val flushManager: FlushManager,
    private val repository: QueueRepository,
    private val restClient: RestClient,
    private val pixelHosts: PixelHosts,
    private val scope: CoroutineScope,
    private val connectivityStatusProvider: ConnectivityStatusProvider
) {

    private val mutex = Mutex()

    operator fun invoke(skipSendingEvents: Boolean) {
        if (!connectivityStatusProvider.isReachable()) {
            Log.d("Network unreachable. Not flushing.")
            return
        }
        scope.launch {
            mutex.withLock {
                val eventsToSend = repository.getStoredQueue()

                if (eventsToSend.isEmpty()) {
                    flushManager.stop()
                    return@launch
                }

                if (skipSendingEvents) {
                    Log.d("Debug mode on. Not sending to Parse.ly. Otherwise, would sent ${eventsToSend.size} events")
                    repository.remove(eventsToSend)
                    return@launch
                }

                val (byHost, unresolved) = pixelHosts.group(eventsToSend)

                if (unresolved.isNotEmpty()) {
                    // Removed rather than retained: the baked map cannot change while the app
                    // runs, so these would never resolve and would grow the stored queue forever.
                    Log.e(
                        "Dropping ${unresolved.size} events whose site ID is not configured. " +
                            "Add every site ID your app uses to parsely-apikeys.json and rebuild."
                    )
                    repository.remove(unresolved)
                }

                for ((host, events) in byHost) {
                    val jsonPayload = toParselyEventsPayload(events)
                    val url = buildPixelUrl(host)
                    Log.d("Sending request with ${events.size} events")
                    Log.d("POST Data $jsonPayload")
                    Log.d("Requested $url")
                    restClient.send(url, jsonPayload)
                        .fold(
                            onSuccess = {
                                Log.i("Pixel request success")
                                repository.remove(events)
                            },
                            onFailure = {
                                Log.e("Pixel request exception", it)
                            }
                        )
                }
            }
        }
    }
}
