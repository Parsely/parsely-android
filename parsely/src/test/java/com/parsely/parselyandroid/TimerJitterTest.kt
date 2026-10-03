package com.parsely.parselyandroid

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test

class TimerJitterTest {

    @After
    fun tearDown() {
        TimerJitter.enabled = true
    }

    @Test
    fun `when enabled, then draw varying factors within the range`() {
        val draws = List(200) { TimerJitter.between(0.8, 1.2) }

        assertThat(draws).allSatisfy { assertThat(it).isGreaterThanOrEqualTo(0.8).isLessThan(1.2) }
        assertThat(draws.distinct().size).isGreaterThan(1)
    }

    @Test
    fun `when disabled, then always return 1`() {
        TimerJitter.enabled = false

        assertThat(List(20) { TimerJitter.between(0.8, 1.2) }).containsOnly(1.0)
    }
}
