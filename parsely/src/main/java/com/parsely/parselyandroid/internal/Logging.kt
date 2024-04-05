package com.parsely.parselyandroid.internal

import java.util.Formatter

internal object Logging {

    /**
     * Log a message to the console.
     */
    @JvmStatic
    fun log(logString: String, vararg objects: Any?) {
        if (logString == "") {
            return
        }
        println(Formatter().format("[Parsely] $logString", *objects).toString())
    }
}
