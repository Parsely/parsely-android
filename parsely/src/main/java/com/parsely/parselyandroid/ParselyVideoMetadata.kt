package com.parsely.parselyandroid

/**
 * ParselyMetadata for video content.
 */
class ParselyVideoMetadata
/**
 * Create a new ParselyVideoMetadata object.
 *
 * @param authors         The names of the authors of the video. Up to 10 authors are accepted.
 * @param videoId         Unique identifier for the video. Required.
 * @param section         The category or vertical to which this video belongs.
 * @param tags            User-defined tags for the video. Up to 20 are allowed.
 * @param thumbUrl        URL at which the main image for this video is located.
 * @param title           The title of the video.
 * @param publicationDateMilliseconds         The timestamp in milliseconds this video was published.
 * @param durationSeconds Duration of the video in seconds. Required.
 */(
    authors: List<String>? = null,
    videoId: String,
    section: String? = null,
    tags: List<String>? = null,
    thumbUrl: String? = null,
    title: String? = null,
    publicationDateMilliseconds: Long? = null,
    @JvmField internal val durationSeconds: Int
) : ParselyMetadata(authors, videoId, section, tags, thumbUrl, title, publicationDateMilliseconds) {
    /**
     * Turn this object into a Map
     *
     * @return a Map object representing the metadata.
     */
    override fun toMap(): Map<String, Any?>? {
        val output = super.toMap()?.toMutableMap()
        output?.put("duration", durationSeconds)
        return output
    }
}
