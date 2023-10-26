package com.parsely.parselyandroid;

import androidx.annotation.NonNull;

import java.util.Calendar;

class UpdateEngagementIntervalCalculator {

    static final long MAX_TIME_BETWEEN_HEARTBEATS = 60 * 60;
    static final long OFFSET_MATCHING_BASE_INTERVAL = 35;
    static final double BACKOFF_PROPORTION = 0.3;

    @NonNull private final Clock clock;

    public UpdateEngagementIntervalCalculator(@NonNull Clock clock) {
        this.clock = clock;
    }

    long updateLatestInterval(@NonNull final Calendar startTime) {
        long totalTrackedTime = (clock.getNow() - startTime.getTime().getTime()) / 1000;
        double totalWithOffset = totalTrackedTime + OFFSET_MATCHING_BASE_INTERVAL;
        double newInterval = totalWithOffset * BACKOFF_PROPORTION;
        long clampedNewInterval = (long) Math.min(MAX_TIME_BETWEEN_HEARTBEATS, newInterval);
        return clampedNewInterval * 1000;
    }
}
