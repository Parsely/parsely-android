package com.parsely.parselyandroid

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper

/** The result of partitioning a flush batch by the host each event must be sent to. */
internal data class GroupedEvents(
    val byHost: Map<String, List<Map<String, Any?>?>>,
    val unresolved: List<Map<String, Any?>?>,
)

/**
 * The site-ID-to-host map baked into the app at build time.
 *
 * There is deliberately no default host. A site ID that is not in the map does not resolve,
 * and its events are dropped rather than sent somewhere that might be the wrong region.
 */
internal class PixelHosts(private val hosts: Map<String, String>) {

    val isEmpty: Boolean
        get() = hosts.isEmpty()

    fun hostFor(siteId: String?): String? {
        if (siteId.isNullOrBlank()) return null
        return hosts[siteId]
    }

    /**
     * Partition events by the host they must be sent to.
     *
     * Grouping is by host rather than by site ID so that several site IDs in the same region
     * share a single request, as they did before hosts were configurable.
     */
    fun group(events: List<Map<String, Any?>?>): GroupedEvents {
        val byHost = mutableMapOf<String, MutableList<Map<String, Any?>?>>()
        val unresolved = mutableListOf<Map<String, Any?>?>()

        for (event in events) {
            val host = hostFor(event?.get(SITE_ID_KEY) as? String)
            if (host == null) {
                unresolved += event
            } else {
                byHost.getOrPut(host) { mutableListOf() } += event
            }
        }

        return GroupedEvents(byHost, unresolved)
    }

    companion object {
        private const val SITE_ID_KEY = "idsite"
        private const val SUPPORTED_VERSION = 1
        internal const val ASSET_NAME = "parsely-hosts.json"

        fun fromJson(json: String): PixelHosts {
            val parsed = try {
                ObjectMapper().readTree(json)
            } catch (ex: Exception) {
                Log.e("Could not read $ASSET_NAME. No events will be sent.", ex)
                return PixelHosts(emptyMap())
            }

            if (parsed.path("version").asInt() != SUPPORTED_VERSION) {
                Log.e("$ASSET_NAME has an unsupported version. No events will be sent.")
                return PixelHosts(emptyMap())
            }

            val hostsNode = parsed.get("hosts") ?: return PixelHosts(emptyMap())
            val hosts = hostsNode.fields().asSequence()
                .mapNotNull { (key, value) -> value.asText().takeIf { it.isNotBlank() }?.let { key to it } }
                .toMap()

            return PixelHosts(hosts)
        }

        fun fromAssets(context: Context): PixelHosts {
            return try {
                context.applicationContext.assets.open(ASSET_NAME).use {
                    fromJson(it.readBytes().decodeToString())
                }
            } catch (ex: Exception) {
                Log.e(
                    "$ASSET_NAME is missing. No events will be sent. Apply the Parse.ly Gradle plugin to generate it.",
                    ex
                )
                PixelHosts(emptyMap())
            }
        }
    }
}

/** Build the collection endpoint for a host. Tolerates a scheme so a hand-edited file cannot double it. */
internal fun buildPixelUrl(host: String): String {
    val bareHost = host.removePrefix("https://").removePrefix("http://")
    return "https://$bareHost/mobileproxy"
}
