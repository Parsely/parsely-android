package com.parsely.parselyandroid

import java.util.Calendar
import java.util.TimeZone

open class Clock {
    open val now
        get() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis
}
