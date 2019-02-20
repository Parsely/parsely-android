package com.parsely.parselyandroid;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;

/* \brief ParselyMetadata for video content.
 *
 */
public class ParselyVideoMetadata extends ParselyMetadata {

    public int durationSeconds;

    /* \brief Create a new ParselyVideoMetadata object.
     *
     * @param authors         List of authors for the video.
     * @param videoId         Unique identifier for the video. Required.
     * @param section         Section of the video.
     * @param tags            List of tags for the video.
     * @param thumbUrl        URL of a thumbnail for the video.
     * @param title           Title of the video.
     * @param pubDate         Publish date of the video.
     * @param durationSeconds Duration of the video in seconds. Required.
     */
    public ParselyVideoMetadata(
            ArrayList<String> authors,
            @NonNull String videoId,
            String section,
            ArrayList<String> tags,
            String thumbUrl,
            String title,
            Calendar pubDate,
            @NonNull int durationSeconds
    ) {
        super(authors, videoId, section, tags, thumbUrl, title, pubDate);
        if (videoId == null) {
            throw new NullPointerException("videoId cannot be null");
        }
        this.durationSeconds = durationSeconds;
    }

    /* \brief Turn this object into a Map
     *
     * @return a Map object representing the metadata.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> output = super.toMap();
        output.put("duration", this.durationSeconds);
        return output;
    }
}
