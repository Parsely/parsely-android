package com.parsely.parselyandroid

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.MapAssert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventsBuilderTest {
    private lateinit var sut: EventsBuilder

    @Before
    fun setUp() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        sut = EventsBuilder(
            applicationContext,
            TEST_SITE_ID,
        )
    }

    @Test
    fun `when building pageview event, then build the correct one`() {
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
            .containsEntry("action", "pageview")
            .containsEntry("pvid", TEST_UUID)
            .sharedPixelAssertions()
    }

    @Test
    fun `when building heartbeat event, then build the correct one`() {
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
            .containsEntry("action", "heartbeat")
            .containsEntry("pvid", TEST_UUID)
            .sharedPixelAssertions()
    }

    @Test
    fun `when building videostart event, then build the correct one`() {
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
            .containsEntry("action", "videostart")
            .containsEntry("vsid", TEST_UUID)
            .sharedPixelAssertions()
    }

    @Test
    fun `when building vheartbeat event, then build the correct one`() {
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
            .containsEntry("action", "vheartbeat")
            .containsEntry("vsid", TEST_UUID)
            .sharedPixelAssertions()
    }

    @Test
    fun `given extraData is null, when creating a pixel, don't include extraData`() {
        // given
        val extraData: Map<String, Any>? = null

        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "pageview",
            null,
            extraData,
            TEST_UUID,
        )

        // then
        @Suppress("UNCHECKED_CAST")
        assertThat(event["data"] as Map<String, Any>).hasSize(5)
    }

    @Test
    fun `given extraData is not null, when creating a pixel, include extraData`() {
        // given
        val extraData: Map<String, Any> = mapOf(
            "extra 1" to "data 1",
            "extra 2" to "data 2"
        )

        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "pageview",
            null,
            extraData,
            TEST_UUID,
        )

        // then
        @Suppress("UNCHECKED_CAST")
        assertThat(event["data"] as Map<String, Any>).hasSize(7)
            .containsAllEntriesOf(extraData)
    }

    @Test
    fun `given metadata is null, when creating a pixel, don't include metadata`() {
        // given
        val metadata: ParselyMetadata? = null

        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "pageview",
            metadata,
            null,
            TEST_UUID,
        )

        // then
        assertThat(event).doesNotContainKey("metadata")
    }

    @Test
    fun `given metadata is not null, when creating a pixel, include metadata`() {
        // given
        val metadata = ParselyMetadata(
            ArrayList<String>(), "link", "section", null, null, null, 0
        )

        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "pageview",
            metadata,
            null,
            TEST_UUID,
        )

        // then
        assertThat(event).containsKey("metadata")
    }

    private fun MapAssert<String, Any>.sharedPixelAssertions() =
        hasSize(6)
            .containsEntry("url", TEST_URL)
            .containsEntry("urlref", "")
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

    companion object {
        const val TEST_SITE_ID = "Example"
        const val TEST_URL = "http://example.com/some-old/article.html"
        const val TEST_UUID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
