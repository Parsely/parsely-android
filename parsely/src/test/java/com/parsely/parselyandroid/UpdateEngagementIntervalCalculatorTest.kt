package com.parsely.parselyandroid

import org.junit.Before

internal class UpdateEngagementIntervalCalculatorTest {

    private lateinit var sut: UpdateEngagementIntervalCalculator
    val fakeClock = FakeClock()

    @Before
    fun setUp() {
        sut = UpdateEngagementIntervalCalculator(fakeClock)
    }

    class FakeClock : Clock() {
        var fakeNow = 0L

        override val now: Long
            get() = fakeNow
    }
}