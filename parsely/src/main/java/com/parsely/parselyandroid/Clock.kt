package com.parsely.parselyandroid

import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

internal open class Clock {
    internal open val now: Duration
        get() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis.milliseconds
}

/** Time since an arbitrary origin. It never jumps when the wall clock is changed, so use it to time waits. */
internal class MonotonicClock : Clock() {
    override val now: Duration
        get() = System.nanoTime().nanoseconds
}
