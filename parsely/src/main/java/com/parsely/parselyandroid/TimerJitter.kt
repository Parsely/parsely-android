package com.parsely.parselyandroid

import kotlin.random.Random

/** Random factors for timer intervals, so devices that start together do not stay in step. */
internal object TimerJitter {
    // Instrumented tests that pin exact timings turn this off.
    @Volatile
    var enabled = true

    fun between(min: Double, max: Double): Double = if (enabled) Random.nextDouble(min, max) else 1.0
}
