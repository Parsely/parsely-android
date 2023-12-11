package com.parsely.parselyandroid

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class AndroidIdProviderTest {

    lateinit var sut: AndroidIdProvider

    @Before
    fun setUp() {
        sut = AndroidIdProvider(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `given no site uuid is stored, when requesting uuid, then return ANDROID_ID value`() {
        // given
        val fakeAndroidId = "test id"
        Settings.Secure.putString(
            ApplicationProvider.getApplicationContext<Application>().contentResolver,
            Settings.Secure.ANDROID_ID,
            fakeAndroidId
        )

        // when
        val result=  sut.provide()

        // then
        assertThat(result).isEqualTo(fakeAndroidId)
    }

    @Test
    fun `given site uuid already requested, when requesting uuid, then return same uuid`() {
        // given
        val fakeAndroidId = "test id"
        Settings.Secure.putString(
            ApplicationProvider.getApplicationContext<Application>().contentResolver,
            Settings.Secure.ANDROID_ID,
            fakeAndroidId
        )
        val storedValue = sut.provide()

        // when
        val result = sut.provide()

        // then
        assertThat(result).isEqualTo(storedValue)
    }
}
