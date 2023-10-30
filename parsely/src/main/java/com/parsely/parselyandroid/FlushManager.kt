package com.parsely.parselyandroid;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Manager for the event flush timer.
 * <p>
 * Handles stopping and starting the flush timer. The flush timer
 * controls how often we send events to Parse.ly servers.
 */
class FlushManager {

    private final ParselyTracker parselyTracker;
    private final Timer parentTimer;
    private final long intervalMillis;
    private TimerTask runningTask;

    public FlushManager(ParselyTracker parselyTracker, Timer parentTimer, long intervalMillis) {
        this.parselyTracker = parselyTracker;
        this.parentTimer = parentTimer;
        this.intervalMillis = intervalMillis;
    }

    public void start() {
        if (runningTask != null) {
            return;
        }

        runningTask = new TimerTask() {
            public void run() {
                parselyTracker.flushEvents();
            }
        };
        parentTimer.scheduleAtFixedRate(runningTask, intervalMillis, intervalMillis);
    }

    public boolean stop() {
        if (runningTask == null) {
            return false;
        } else {
            boolean output = runningTask.cancel();
            runningTask = null;
            return output;
        }
    }

    public boolean isRunning() {
        return runningTask != null;
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }
}
