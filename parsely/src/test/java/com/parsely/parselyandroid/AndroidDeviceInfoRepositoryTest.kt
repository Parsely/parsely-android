package com.parsely.parselyandroid

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

private const val SDK_VERSION = 33
private const val MANUFACTURER = "test manufacturer"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SDK_VERSION])
internal class AndroidDeviceInfoRepositoryTest {

    @Before
    fun setUp() {
        ShadowBuild.setManufacturer(MANUFACTURER)
    }

    @Test
    fun `given the advertisement id exists, when collecting device info, then parsely site uuid is advertisement id`() {
        // given
        val advertisementId = "ad id"
        val sut = AndroidDeviceInfoRepository(
            advertisementIdProvider = { advertisementId },
            androidIdProvider = { "android id" })

        // when
        val result = sut.collectDeviceInfo()

        // then
        assertThat(result).isEqualTo(expectedConstantDeviceInfo + ("parsely_site_uuid" to advertisementId))
    }

    @Test
    fun `given the advertisement is null and android id is not, when collecting device info, then parsely id is android id`() {
        // given
        val androidId = "android id"
        val sut = AndroidDeviceInfoRepository(
            advertisementIdProvider = { null },
            androidIdProvider = { androidId }
        )

        // when
        val result = sut.collectDeviceInfo()

        // then
        assertThat(result).isEqualTo(expectedConstantDeviceInfo + ("parsely_site_uuid" to androidId))
    }

    @Test
    fun `given both advertisement id and android id are null, when collecting device info, then parsely id is empty`() {
        // given
        val sut = AndroidDeviceInfoRepository(
            advertisementIdProvider = { null },
            androidIdProvider = { null }
        )

        // when
        val result = sut.collectDeviceInfo()

        // then
        assertThat(result).isEqualTo(expectedConstantDeviceInfo + ("parsely_site_uuid" to ""))
    }

    private companion object {
        val expectedConstantDeviceInfo = mapOf(
            "manufacturer" to MANUFACTURER,
            "os" to "android",
            "os_version" to "$SDK_VERSION"
        )
    }
}
