package com.parsely.parselyandroid

import android.util.Log

internal object Log {

    fun i(message: String) {
        Log.i("Parsely", message)
    }

    fun d(message: String) {
        Log.d("Parsely", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e("Parsely", message, throwable)
    }
}
