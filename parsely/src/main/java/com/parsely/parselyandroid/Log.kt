package com.parsely.parselyandroid

import android.util.Log as AndroidLog

internal object AndroidLogWrapper : Log {

    private const val TAG = "Parsely"

    override fun i(message: String) {
        AndroidLog.i(TAG, message)
    }

    override fun d(message: String) {
        AndroidLog.d(TAG, message)
    }

    override fun e(message: String, throwable: Throwable?) {
        AndroidLog.e(TAG, message, throwable)
    }
}

internal interface Log {
    fun i(message: String)
    fun d(message: String)
    fun e(message: String, throwable: Throwable?)

    companion object {
        var instance: Log = AndroidLogWrapper

        fun i(message: String) = instance.i(message)
        fun d(message: String) = instance.d(message)
        fun e(message: String, throwable: Throwable? = null) = instance.e(message, throwable)
    }
}
