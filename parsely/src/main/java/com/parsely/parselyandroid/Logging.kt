package com.parsely.parselyandroid

import java.util.Formatter

object Logging {

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
