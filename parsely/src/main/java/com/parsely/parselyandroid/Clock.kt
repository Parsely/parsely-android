package com.parsely.parselyandroid

import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration.Companion.milliseconds

open class Clock {
    open val now
        get() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis.milliseconds
}
