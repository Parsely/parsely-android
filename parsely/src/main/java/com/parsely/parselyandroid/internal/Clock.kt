package com.parsely.parselyandroid.internal

import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal open class Clock {
    internal open val now: Duration
        get() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis.milliseconds
}
