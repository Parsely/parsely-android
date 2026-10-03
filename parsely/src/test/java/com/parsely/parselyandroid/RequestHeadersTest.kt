package com.parsely.parselyandroid

import android.os.Build
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RequestHeadersTest {

    @Test
    fun `when building headers, then name the app, OS, device and SDK version, and send the site id`() {
        val headers = RequestHeaders.build(
            appName = "com.example.news",
            appVersion = "5.12.0",
            osVersion = "14",
            deviceModel = "Pixel 7",
            siteId = "example.com",
            sdkVersion = "4.2.1",
        )

        assertThat(headers).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "User-Agent" to "com.example.news/5.12.0 Android/14 (Pixel 7) ParselySDK/android/4.2.1",
                "X-Parsely-Apikey" to "example.com",
            )
        )
    }

    @Test
    fun `given values with spaces, parentheses or non-ASCII characters, when building headers, then keep the User-Agent printable ASCII`() {
        val headers = RequestHeaders.build(
            appName = "com.example.news",
            appVersion = "5.12.0 (beta)",
            osVersion = "14",
            deviceModel = "Galaxy (S24) Ultra ™ 华为",
            siteId = "example.com",
            sdkVersion = "4.2.1",
        )

        assertThat(headers["User-Agent"])
            .isEqualTo("com.example.news/5.12.0__beta_ Android/14 (Galaxy _S24_ Ultra _ __) ParselySDK/android/4.2.1")
            .matches("[ -~]+")
    }

    @Test
    fun `given missing app version and device details, when building headers, then mark them unknown`() {
        val headers = RequestHeaders.build(
            appName = "com.example.news",
            appVersion = null,
            osVersion = "",
            deviceModel = null,
            siteId = "example.com",
            sdkVersion = "4.2.1",
        )

        assertThat(headers["User-Agent"])
            .isEqualTo("com.example.news/unknown Android/unknown (unknown) ParselySDK/android/4.2.1")
    }

    @Test
    fun `given a blank site id, when building headers, then send no apikey header`() {
        val headers = RequestHeaders.build("app", "1.0", "14", "Pixel 7", siteId = "  ")

        assertThat(headers).containsOnlyKeys("User-Agent")
    }

    @Test
    fun `given a site id that is not printable ASCII, when building headers, then send no apikey header`() {
        val headers = RequestHeaders.build("app", "1.0", "14", "Pixel 7", siteId = "café.com")

        assertThat(headers).containsOnlyKeys("User-Agent")
    }

    @Test
    fun `when building headers from a context, then use the app's package and the device build`() {
        val context = RuntimeEnvironment.getApplication()

        val headers = RequestHeaders.from(context, "example.com")

        assertThat(headers["User-Agent"])
            .startsWith("${context.packageName}/")
            .contains(" Android/${Build.VERSION.RELEASE} (${Build.MODEL}) ")
            .endsWith(" ParselySDK/android/$SDK_VERSION")
        assertThat(headers["X-Parsely-Apikey"]).isEqualTo("example.com")
    }

    @Test
    fun `then the SDK version comes from the build`() {
        assertThat(SDK_VERSION).isNotBlank().doesNotContain(" ")
    }
}
