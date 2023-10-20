package com.parsely.parselyandroid;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.TimeZone;

class UpdateEngagementIntervalCalculator {

    private static final long MAX_TIME_BETWEEN_HEARTBEATS = 60 * 60;
    private static final long OFFSET_MATCHING_BASE_INTERVAL = 35;
    private static final double BACKOFF_PROPORTION = 0.3;

    long updateLatestInterval(@NonNull final Calendar startTime) {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        long totalTrackedTime = (now.getTime().getTime() - startTime.getTime().getTime()) / 1000;
        double totalWithOffset = totalTrackedTime + OFFSET_MATCHING_BASE_INTERVAL;
        double newInterval = totalWithOffset * BACKOFF_PROPORTION;
        long clampedNewInterval = (long) Math.min(MAX_TIME_BETWEEN_HEARTBEATS, newInterval);
        System.out.println("New interval: " + clampedNewInterval*1000);
        return clampedNewInterval * 1000;
    }
}
