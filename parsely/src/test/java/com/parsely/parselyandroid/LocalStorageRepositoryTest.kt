package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalStorageRepositoryTest {

    private lateinit var sut: LocalStorageRepository

    @Before
    fun setUp() {
        sut = LocalStorageRepository(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `when expelling stored event, then assert that it has no effect`() {
        // given
        sut.persistQueue((1..100).map { mapOf("index" to it) })

        // when
        sut.expelStoredEvent()

        // then
        assertThat(sut.storedQueue).hasSize(100)
    }
}
