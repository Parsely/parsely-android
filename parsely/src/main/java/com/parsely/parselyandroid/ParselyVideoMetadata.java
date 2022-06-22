package com.parsely.parselyandroid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
     * @param authors         The names of the authors of the video. Up to 10 authors are accepted.
     * @param videoId         Unique identifier for the video. Required.
     * @param section         The category or vertical to which this video belongs.
     * @param tags            User-defined tags for the video. Up to 20 are allowed.
     * @param thumbUrl        URL at which the main image for this video is located.
     * @param title           The title of the video.
     * @param pubDate         The date this video was published.
     * @param durationSeconds Duration of the video in seconds. Required.
     */
    public ParselyVideoMetadata(
            @Nullable ArrayList<String> authors,
            @NonNull String videoId,
            @Nullable String section,
            @Nullable ArrayList<String> tags,
            @Nullable String thumbUrl,
            @Nullable String title,
            @Nullable Calendar pubDate,
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
