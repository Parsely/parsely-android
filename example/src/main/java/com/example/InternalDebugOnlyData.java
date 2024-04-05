package com.example;

import com.parsely.parselyandroid.ParselyTrackerInternal;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The InternalDebugOnlyData class is designed to provide access to internal data for debugging
 * purposes through reflection. The methods within this class are intended for internal use only
 * and should *not* be utilized within the SDK user's code.
 * @noinspection KotlinInternalInJava
 */
class InternalDebugOnlyData {
    private final ParselyTrackerInternal parselyTracker;

    InternalDebugOnlyData(ParselyTrackerInternal parselyTracker) {
        this.parselyTracker = parselyTracker;
    }

    boolean engagementIsActive() {
        return (boolean) invokePrivateMethod("engagementIsActive");
    }

    @Nullable
    Double getEngagementInterval() {
        return (Double) invokePrivateMethod("getEngagementInterval");
    }

    @Nullable
    Double getVideoEngagementInterval() {
        return (Double) invokePrivateMethod("getVideoEngagementInterval");
    }

    long getFlushInterval() {
        return (long) invokePrivateMethod("getFlushInterval");
    }

    boolean videoIsActive() {
        return (boolean) invokePrivateMethod("videoIsActive");
    }

    boolean flushTimerIsActive() {
        return (boolean) invokePrivateMethod("flushTimerIsActive");
    }

    private Object invokePrivateMethod(String methodName) {
        try {
            Method method = ParselyTrackerInternal.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(parselyTracker);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
