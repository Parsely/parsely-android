package com.parsely.parselyandroid

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PixelHostsTest {

    private fun event(siteId: Any?): Map<String, Any?> = mapOf("idsite" to siteId, "url" to "http://a")

    @Test
    fun `given a known site id, when resolving, then return its host`() {
        val sut = PixelHosts(mapOf("a.com" to "p1.parsely.com"))

        assertThat(sut.hostFor("a.com")).isEqualTo("p1.parsely.com")
    }

    @Test
    fun `given an unknown site id, when resolving, then return null`() {
        val sut = PixelHosts(mapOf("a.com" to "p1.parsely.com"))

        assertThat(sut.hostFor("b.com")).isNull()
    }

    @Test
    fun `given a blank or null site id, when resolving, then return null`() {
        val sut = PixelHosts(mapOf("" to "p1.parsely.com"))

        assertThat(sut.hostFor("")).isNull()
        assertThat(sut.hostFor(null)).isNull()
        assertThat(sut.hostFor("   ")).isNull()
    }

    @Test
    fun `given site ids sharing a host, when grouping, then coalesce into one group`() {
        val sut = PixelHosts(mapOf("a.com" to "p1.parsely.com", "b.com" to "p1.parsely.com"))

        val result = sut.group(listOf(event("a.com"), event("b.com")))

        assertThat(result.byHost).hasSize(1)
        assertThat(result.byHost["p1.parsely.com"]).hasSize(2)
        assertThat(result.unresolved).isEmpty()
    }

    @Test
    fun `given site ids on different hosts, when grouping, then split into two groups`() {
        val sut = PixelHosts(mapOf("a.com" to "p1.parsely.com", "b.com" to "p1-irl.parsely.com"))

        val result = sut.group(listOf(event("a.com"), event("b.com")))

        assertThat(result.byHost).hasSize(2)
        assertThat(result.byHost["p1.parsely.com"]).hasSize(1)
        assertThat(result.byHost["p1-irl.parsely.com"]).hasSize(1)
    }

    @Test
    fun `given an undeclared site id, when grouping, then set it aside as unresolved`() {
        val sut = PixelHosts(mapOf("a.com" to "p1.parsely.com"))

        val result = sut.group(listOf(event("a.com"), event("nope.com")))

        assertThat(result.byHost["p1.parsely.com"]).hasSize(1)
        assertThat(result.unresolved).hasSize(1)
    }

    @Test
    fun `given a null event in the queue, when grouping, then treat it as unresolved and do not crash`() {
        val sut = PixelHosts(mapOf("a.com" to "p1.parsely.com"))

        val result = sut.group(listOf(null, event("a.com")))

        assertThat(result.byHost["p1.parsely.com"]).hasSize(1)
        assertThat(result.unresolved).hasSize(1)
    }

    @Test
    fun `given a non-string idsite, when grouping, then treat it as unresolved`() {
        val sut = PixelHosts(mapOf("a.com" to "p1.parsely.com"))

        val result = sut.group(listOf(event(123)))

        assertThat(result.unresolved).hasSize(1)
    }

    @Test
    fun `given an empty map, when grouping, then everything is unresolved`() {
        val sut = PixelHosts(emptyMap())

        val result = sut.group(listOf(event("a.com")))

        assertThat(sut.isEmpty).isTrue
        assertThat(result.byHost).isEmpty()
        assertThat(result.unresolved).hasSize(1)
    }

    @Test
    fun `given a valid artifact, when parsing, then read its hosts`() {
        val sut = PixelHosts.fromJson(
            """{"version": 1, "generated_at": "2026-09-23T14:02:11Z", "hosts": {"a.com": "p1-irl.parsely.com"}}"""
        )

        assertThat(sut.hostFor("a.com")).isEqualTo("p1-irl.parsely.com")
    }

    @Test
    fun `given malformed json, when parsing, then return an empty map`() {
        assertThat(PixelHosts.fromJson("{ not json").isEmpty).isTrue
    }

    @Test
    fun `given an unsupported version, when parsing, then return an empty map`() {
        assertThat(PixelHosts.fromJson("""{"version": 99, "hosts": {"a.com": "p1.parsely.com"}}""").isEmpty).isTrue
    }

    @Test
    fun `given no hosts key, when parsing, then return an empty map`() {
        assertThat(PixelHosts.fromJson("""{"version": 1}""").isEmpty).isTrue
    }

    @Test
    fun `given a bare host, when building a url, then produce the mobileproxy endpoint`() {
        assertThat(buildPixelUrl("p1-irl.parsely.com")).isEqualTo("https://p1-irl.parsely.com/mobileproxy")
    }

    @Test
    fun `given a host that already carries a scheme, when building a url, then do not double it`() {
        assertThat(buildPixelUrl("https://p1.parsely.com")).isEqualTo("https://p1.parsely.com/mobileproxy")
    }

    @Test
    fun `given an entry with a blank host, when parsing, then drop only that site id`() {
        val sut = PixelHosts.fromJson(
            """{"version": 1, "hosts": {"blank.com": "", "known.com": "p1.parsely.com"}}"""
        )

        assertThat(sut.hostFor("blank.com")).isNull()
        assertThat(sut.hostFor("known.com")).isEqualTo("p1.parsely.com")
    }

    @Test
    fun `given a baked asset, when loading from assets, then read its hosts`() {
        val sut = PixelHosts.fromAssets(ApplicationProvider.getApplicationContext<Context>())

        assertThat(sut.hostFor("asset.example.com")).isEqualTo("p1-irl.parsely.com")
    }
}
