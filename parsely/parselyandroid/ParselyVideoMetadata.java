package com.parsely.parselyandroid;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;

public class ParselyVideoMetadata extends ParselyMetadata {

    public int durationSeconds;

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
        this.durationSeconds = durationSeconds;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> output = super.toMap();
        output.put("durationSeconds", this.durationSeconds);
        return output;
    }
}
