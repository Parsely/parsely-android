package com.parsely.parselyandroid

internal object FakeLog : Log {
    override fun i(message: String) = println(message)
    override fun d(message: String) = println(message)
    override fun e(message: String, throwable: Throwable?) = println(message + throwable?.message)
}
