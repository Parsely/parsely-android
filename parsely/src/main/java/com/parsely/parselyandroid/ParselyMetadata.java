package com.parsely.parselyandroid;

import androidx.annotation.Nullable;

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
    public String link, section, thumbUrl, title;
    public Calendar pubDate;

    /* \brief Create a new ParselyMetadata object.
     *
     * @param authors         The names of the authors of the content. Up to 10 authors are accepted.
     * @param link            A post's canonical url.
     * @param section         The category or vertical to which this content belongs.
     * @param tags            User-defined tags for the content. Up to 20 are allowed.
     * @param thumbUrl        URL at which the main image for this content is located.
     * @param title           The title of the content.
     * @param pubDate         The date this piece of content was published.
     */
    public ParselyMetadata(
            @Nullable ArrayList<String> authors,
            @Nullable String link,
            @Nullable String section,
            @Nullable ArrayList<String> tags,
            @Nullable String thumbUrl,
            @Nullable String title,
            @Nullable Calendar pubDate
    ) {
        this.authors = authors;
        this.link = link;
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
        if (this.authors != null) {
            output.put("authors", this.authors);
        }
        if (this.link != null) {
            output.put("link", this.link);
        }
        if (this.section != null) {
            output.put("section", this.section);
        }
        if (this.tags != null) {
            output.put("tags", this.tags);
        }
        if (this.thumbUrl != null) {
            output.put("thumb_url", this.thumbUrl);
        }
        if (this.title != null) {
            output.put("title", this.title);
        }
        if (this.pubDate != null) {
            output.put("pub_date_tmsp", this.pubDate.getTimeInMillis() / 1000);
        }
        return output;
    }
}

