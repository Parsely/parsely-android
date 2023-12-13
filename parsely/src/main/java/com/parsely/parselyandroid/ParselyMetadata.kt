package com.parsely.parselyandroid

import java.util.Calendar

/**
 * Represents post metadata to be passed to Parsely tracking.
 *
 *
 * This class is used to attach a metadata block to a Parse.ly pageview
 * request. Pageview metadata is only required for URLs not accessible over the
 * internet (i.e. app-only content) or if the customer is using an "in-pixel" integration.
 * Otherwise, metadata will be gathered by Parse.ly's crawling infrastructure.
 */
open class ParselyMetadata
/**
 * Create a new ParselyMetadata object.
 *
 * @param authors  The names of the authors of the content. Up to 10 authors are accepted.
 * @param link     A post's canonical url.
 * @param section  The category or vertical to which this content belongs.
 * @param tags     User-defined tags for the content. Up to 20 are allowed.
 * @param thumbUrl URL at which the main image for this content is located.
 * @param title    The title of the content.
 * @param pubDate  The date this piece of content was published.
 */(
    private val authors: List<String>? = null,
    @JvmField internal val link: String? = null,
    private val section: String? = null,
    private val tags: List<String>? = null,
    private val thumbUrl: String? = null,
    private val title: String? = null,
    private val pubDate: Calendar? = null
) {
    /**
     * Turn this object into a Map
     *
     * @return a Map object representing the metadata.
     */
    open fun toMap(): Map<String, Any?>? {
        val output: MutableMap<String, Any?> = HashMap()
        if (authors != null) {
            output["authors"] = authors
        }
        if (link != null) {
            output["link"] = link
        }
        if (section != null) {
            output["section"] = section
        }
        if (tags != null) {
            output["tags"] = tags
        }
        if (thumbUrl != null) {
            output["thumb_url"] = thumbUrl
        }
        if (title != null) {
            output["title"] = title
        }
        if (pubDate != null) {
            output["pub_date_tmsp"] = pubDate.timeInMillis / 1000
        }
        return output
    }
}
