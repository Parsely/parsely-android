package com.parsely.parselyandroid;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Engagement manager for article and video engagement.
 * <p>
 * Implemented to handle its own queuing of future executions to accomplish
 * two things:
 * <p>
 * 1. Flushing any engaged time before canceling.
 * 2. Progressive backoff for long engagements to save data.
 */
class EngagementManager {

    private final ParselyTracker parselyTracker;
    public Map<String, Object> baseEvent;
    private boolean started;
    private final Timer parentTimer;
    private TimerTask waitingTimerTask;
    private long latestDelayMillis, totalTime;
    private Calendar startTime;



    public EngagementManager(ParselyTracker parselyTracker, Timer parentTimer, long intervalMillis, Map<String, Object> baseEvent) {
        this.parselyTracker = parselyTracker;
        this.baseEvent = baseEvent;
        this.parentTimer = parentTimer;
        latestDelayMillis = intervalMillis;
        totalTime = 0;
        startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    public boolean isRunning() {
        return started;
    }

    public void start() {
        scheduleNextExecution(latestDelayMillis);
        started = true;
        startTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    public void stop() {
        waitingTimerTask.cancel();
        started = false;
    }

    public boolean isSameVideo(String url, String urlRef, ParselyVideoMetadata metadata) {
        Map<String, Object> baseMetadata = (Map<String, Object>) baseEvent.get("metadata");
        return (baseEvent.get("url").equals(url) &&
                baseEvent.get("urlref").equals(urlRef) &&
                baseMetadata.get("link").equals(metadata.link) &&
                (int) (baseMetadata.get("duration")) == metadata.durationSeconds);
    }

    private void scheduleNextExecution(long delay) {
        TimerTask task = new TimerTask() {
            public void run() {
                doEnqueue(scheduledExecutionTime());
                updateLatestInterval();
                scheduleNextExecution(latestDelayMillis);
            }

            public boolean cancel() {
                boolean output = super.cancel();
                // Only enqueue when we actually canceled something. If output is false then
                // this has already been canceled.
                if (output) {
                    doEnqueue(scheduledExecutionTime());
                }
                return output;
            }
        };
        latestDelayMillis = delay;
        parentTimer.schedule(task, delay);
        waitingTimerTask = task;
    }

    private void doEnqueue(long scheduledExecutionTime) {
        // Create a copy of the base event to enqueue
        Map<String, Object> event = new HashMap<>(baseEvent);
        ParselyTracker.PLog(String.format("Enqueuing %s event.", event.get("action")));

        // Update `ts` for the event since it's happening right now.
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        @SuppressWarnings("unchecked")
        Map<String, Object> baseEventData = (Map<String, Object>) event.get("data");
        assert baseEventData != null;
        Map<String, Object> data = new HashMap<>((Map<String, Object>) baseEventData);
        data.put("ts", now.getTimeInMillis());
        event.put("data", data);

        // Adjust inc by execution time in case we're late or early.
        long executionDiff = (System.currentTimeMillis() - scheduledExecutionTime);
        long inc = (latestDelayMillis + executionDiff);
        totalTime += inc;
        event.put("inc", inc / 1000);
        event.put("tt", totalTime);

        parselyTracker.enqueueEvent(event);
    }


    public double getIntervalMillis() {
        return latestDelayMillis;
    }
}
