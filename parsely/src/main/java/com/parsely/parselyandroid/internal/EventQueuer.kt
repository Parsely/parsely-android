package com.parsely.parselyandroid.internal

internal interface EventQueuer {
    fun enqueueEvent(event: Map<String, Any>)
}
