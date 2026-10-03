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
    fun `given the system User-Agent, when building headers, then append it so device brand and model still parse`() {
        val headers = RequestHeaders.build(
            appName = "com.example.news",
            appVersion = "5.12.0",
            osVersion = "14",
            deviceModel = "Pixel 7",
            siteId = "example.com",
            sdkVersion = "4.2.1",
            systemUserAgent = "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 7 Build/UP1A.231005.007)",
        )

        assertThat(headers["User-Agent"]).isEqualTo(
            "com.example.news/5.12.0 Android/14 (Pixel 7) ParselySDK/android/4.2.1 " +
                "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 7 Build/UP1A.231005.007)"
        )
    }

    @Test
    fun `given a system User-Agent with non-ASCII characters, when building headers, then keep the User-Agent printable ASCII`() {
        val headers = RequestHeaders.build(
            "app", "1.0", "14", "Pixel 7", siteId = "example.com", sdkVersion = "4.2.1",
            systemUserAgent = "Dalvik/2.1.0 (Linux; U; Android 14; Galaxy ™ Build/X)",
        )

        assertThat(headers["User-Agent"])
            .isEqualTo("app/1.0 Android/14 (Pixel 7) ParselySDK/android/4.2.1 Dalvik/2.1.0 (Linux; U; Android 14; Galaxy _ Build/X)")
            .matches("[ -~]+")
    }

    @Test
    fun `when building headers from a context, then use the app's package, the device build and the system User-Agent`() {
        val context = RuntimeEnvironment.getApplication()
        val previous = System.getProperty("http.agent")
        System.setProperty("http.agent", "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 7 Build/UP1A.231005.007)")

        val headers = try {
            RequestHeaders.from(context, "example.com")
        } finally {
            if (previous == null) System.clearProperty("http.agent") else System.setProperty("http.agent", previous)
        }

        assertThat(headers["User-Agent"])
            .startsWith("${context.packageName}/")
            .contains(" Android/${Build.VERSION.RELEASE} (${Build.MODEL}) ")
            .endsWith(" ParselySDK/android/$SDK_VERSION Dalvik/2.1.0 (Linux; U; Android 14; Pixel 7 Build/UP1A.231005.007)")
        assertThat(headers["X-Parsely-Apikey"]).isEqualTo("example.com")
    }

    @Test
    fun `then the SDK version comes from the build`() {
        assertThat(SDK_VERSION).isNotBlank().doesNotContain(" ")
    }
}
