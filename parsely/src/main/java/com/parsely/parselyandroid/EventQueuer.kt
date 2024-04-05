package com.parsely.parselyandroid

internal interface EventQueuer {
    fun enqueueEvent(event: Map<String, Any>)
}
