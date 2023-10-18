package com.parsely.parselyandroid

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class EventsBuilderTest {

    private lateinit var sut: EventsBuilder

    @Before
    fun setUp() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        sut = EventsBuilder(
            applicationContext,
            TEST_SITE_ID,
        )
        Settings.Secure.putString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID,
            "android_id"
        )
    }

    @Test
    fun `events builder prepares correct pageview pixel`() {
        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "pageview",
            null,
            null,
            TEST_UUID,
        )

        // then
        assertThat(event)
            .hasSize(6)
            .containsEntry("action", "pageview")
            .containsEntry("url", TEST_URL)
            .containsEntry("urlref", "")
            .containsEntry("pvid", TEST_UUID)
            .containsEntry("idsite", TEST_SITE_ID)
            .hasEntrySatisfying("data") {
                @Suppress("UNCHECKED_CAST")
                it as Map<String, Any>
                assertThat(it)
                    .hasSize(5)
                    .containsEntry("os", "android")
                    .hasEntrySatisfying("ts") { timestamp ->
                        assertThat(timestamp as Long).isBetween(1111111111111, 9999999999999)
                    }
                    .containsEntry("manufacturer", "robolectric")
                    .containsEntry("os_version", "33")
                    .containsEntry("parsely_site_uuid", null)
            }
    }

    @Test
    fun `events builder prepares correct heartbeat pixel`() {
        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "heartbeat",
            null,
            null,
            TEST_UUID,
        )

        // then
        assertThat(event)
            .hasSize(6)
            .containsEntry("action", "heartbeat")
            .containsEntry("url", TEST_URL)
            .containsEntry("urlref", "")
            .containsEntry("pvid", TEST_UUID)
            .containsEntry("idsite", TEST_SITE_ID)
            .hasEntrySatisfying("data") {
                @Suppress("UNCHECKED_CAST")
                it as Map<String, Any>
                assertThat(it)
                    .hasSize(5)
                    .containsEntry("os", "android")
                    .hasEntrySatisfying("ts") { timestamp ->
                        assertThat(timestamp as Long).isBetween(1111111111111, 9999999999999)
                    }
                    .containsEntry("manufacturer", "robolectric")
                    .containsEntry("os_version", "33")
                    .containsEntry("parsely_site_uuid", null)
            }
    }

    @Test
    fun `events builder prepares correct videostart pixel`() {
        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "videostart",
            null,
            null,
            TEST_UUID,
        )

        // then
        assertThat(event)
            .hasSize(6)
            .containsEntry("action", "videostart")
            .containsEntry("url", TEST_URL)
            .containsEntry("urlref", "")
            .containsEntry("vsid", TEST_UUID)
            .containsEntry("idsite", TEST_SITE_ID)
            .hasEntrySatisfying("data") {
                @Suppress("UNCHECKED_CAST")
                it as Map<String, Any>
                assertThat(it)
                    .hasSize(5)
                    .containsEntry("os", "android")
                    .hasEntrySatisfying("ts") { timestamp ->
                        assertThat(timestamp as Long).isBetween(1111111111111, 9999999999999)
                    }
                    .containsEntry("manufacturer", "robolectric")
                    .containsEntry("os_version", "33")
                    .containsEntry("parsely_site_uuid", null)
            }
    }

    @Test
    fun `events builder prepares correct vheartbeat pixel`() {
        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "vheartbeat",
            null,
            null,
            TEST_UUID,
        )

        // then
        assertThat(event)
            .hasSize(6)
            .containsEntry("action", "vheartbeat")
            .containsEntry("url", TEST_URL)
            .containsEntry("urlref", "")
            .containsEntry("vsid", TEST_UUID)
            .containsEntry("idsite", TEST_SITE_ID)
            .hasEntrySatisfying("data") {
                @Suppress("UNCHECKED_CAST")
                it as Map<String, Any>
                assertThat(it)
                    .hasSize(5)
                    .containsEntry("os", "android")
                    .hasEntrySatisfying("ts") { timestamp ->
                        assertThat(timestamp as Long).isBetween(1111111111111, 9999999999999)
                    }
                    .containsEntry("manufacturer", "robolectric")
                    .containsEntry("os_version", "33")
                    .containsEntry("parsely_site_uuid", null)
            }
    }

    companion object {
        const val TEST_SITE_ID = "Example"
        const val TEST_URL = "http://example.com/some-old/article.html"
        const val TEST_UUID = "123e4567-e89b-12d3-a456-426614174000"
    }
}