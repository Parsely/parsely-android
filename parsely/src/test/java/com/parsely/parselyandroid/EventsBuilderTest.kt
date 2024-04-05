package com.parsely.parselyandroid

import com.parsely.parselyandroid.internal.Clock
import com.parsely.parselyandroid.internal.DeviceInfoRepository
import com.parsely.parselyandroid.internal.EventsBuilder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.MapAssert
import org.junit.Before
import org.junit.Test

internal class EventsBuilderTest {
    private lateinit var sut: EventsBuilder
    private val clock = FakeClock()

    @Before
    fun setUp() {
        sut = EventsBuilder(
            FakeDeviceInfoRepository(),
            TEST_SITE_ID,
            clock
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
            SiteIdSource.Default,
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
            SiteIdSource.Default,
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
            SiteIdSource.Default,
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
            SiteIdSource.Default,
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
            SiteIdSource.Default,
        )

        // then
        @Suppress("UNCHECKED_CAST")
        assertThat(event["data"] as Map<String, Any>).hasSize(2)
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
            SiteIdSource.Default,
        )

        // then
        @Suppress("UNCHECKED_CAST")
        assertThat(event["data"] as Map<String, Any>).hasSize(4)
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
            SiteIdSource.Default,
        )

        // then
        assertThat(event).doesNotContainKey("metadata")
    }

    @Test
    fun `given metadata is not null, when creating a pixel, include metadata`() {
        // given
        val metadata = ParselyMetadata(
            ArrayList<String>(), "link", "section", null, null, null, null
        )

        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "pageview",
            metadata,
            null,
            TEST_UUID,
            SiteIdSource.Default,
        )

        // then
        assertThat(event).containsKey("metadata")
    }

    @Test
    fun `given default site id is provided, when creating a pixel, then use the default site id`() {
        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "pageview",
            null,
            null,
            TEST_UUID,
            SiteIdSource.Default,
        )

        // then
        assertThat(event).containsEntry("idsite", TEST_SITE_ID)
    }


    @Test
    fun `given custom site id is provided, when creating a pixel, then use the custom site id`() {
        // given
        val customSiteId = "Custom Site ID"

        // when
        val event: Map<String, Any> = sut.buildEvent(
            TEST_URL,
            "",
            "pageview",
            null,
            null,
            TEST_UUID,
            SiteIdSource.Custom(customSiteId),
        )

        // then
        assertThat(event).containsEntry("idsite", customSiteId)
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
                    .hasSize(2)
                    .containsAllEntriesOf(FAKE_DEVICE_INFO)
            }

    companion object {
        const val TEST_SITE_ID = "Example"
        const val TEST_URL = "http://example.com/some-old/article.html"
        const val TEST_UUID = "123e4567-e89b-12d3-a456-426614174000"

        val FAKE_NOW = 15.hours
        val FAKE_DEVICE_INFO = mapOf("device" to "info", "ts" to FAKE_NOW.inWholeMilliseconds.toString())
    }

    class FakeDeviceInfoRepository: DeviceInfoRepository {
        override fun collectDeviceInfo(): Map<String, String> = FAKE_DEVICE_INFO
    }

    class FakeClock() : Clock() {
        override val now: Duration = FAKE_NOW
    }
}
