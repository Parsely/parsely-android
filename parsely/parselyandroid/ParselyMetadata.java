package com.parsely.parselyandroid;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/*! \brief Represents post metadata to be passed to Parsely tracking.
 *
 *  This class is used to attach a metadata block to a Parse.ly pageview
 *  request. Pageview metadata is only required for URLs not accessible over the
 *  internet (i.e. app-only content) or if the customer is using an "in-pixel" integration.
 *  Otherwise, metadata will be gathered by Parse.ly's crawling infrastructure.
 */
public class ParselyMetadata {
    public ArrayList<String> authors, tags;
    public String canonicalUrl, section, thumbUrl, title;
    public Calendar pubDate;

    /* \brief Create a new ParselyMetadata object.
     *
     * @param authors         List of authors for the post.
     * @param canonicalUrl    Canonical URL of the post.
     * @param section         Section of the post.
     * @param tags            List of tags for the post.
     * @param thumbUrl        URL of a thumbnail for the post.
     * @param title           Title of the post.
     * @param pubDate         Publish date of the post.
     */
    public ParselyMetadata(
            @Nullable ArrayList<String> authors,
            @Nullable String canonicalUrl,
            @Nullable String section,
            @Nullable ArrayList<String> tags,
            @Nullable String thumbUrl,
            @Nullable String title,
            @Nullable Calendar pubDate
    ) {
        this.authors = authors;
        this.canonicalUrl = canonicalUrl;
        this.section = section;
        this.tags = tags;
        this.thumbUrl = thumbUrl;
        this.title = title;
        this.pubDate = pubDate;
    }

    /* \brief Turn this object into a Map
     *
     * @return a Map object representing the metadata.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> output = new HashMap<>();
        if (this.authors != null)
            output.put("authors", this.authors);
        if (this.canonicalUrl != null)
            output.put("canonical_url", this.canonicalUrl);
        if (this.section != null)
            output.put("section", this.section);
        if (this.tags != null)
            output.put("tags", this.tags);
        if (this.thumbUrl != null)
            output.put("thumb_url", this.thumbUrl);
        if (this.title != null)
            output.put("title", this.title);
        if (this.pubDate != null)
            output.put("pub_date_tmsp", this.pubDate.getTimeInMillis() / 1000);
        return output;
    }
}

