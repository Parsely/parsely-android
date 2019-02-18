package com.parsely.parselyandroid;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/*! \brief Represents article/video metadata to be passed to Parsely tracking.
 *
 *  This class is used to attach a metadata block to a Parse.ly pageview or video
 *  request. For most use cases this is only required for video data. Pageviews typically
 *  correspond to a URL which we can crawl on the customer site.
 */
public class ParselyMetadata {
    public ArrayList<String> authors, tags;
    public String canonical_url, section, thumbUrl, title;
    public Calendar pubDate;

    public ParselyMetadata(
            ArrayList<String> authors,
            String canonical_url,
            String section,
            ArrayList<String> tags,
            String thumbUrl,
            String title,
            Calendar pubDate
            ) {
        this.authors = authors;
        this.canonical_url = canonical_url;
        this.section = section;
        this.tags = tags;
        this.thumbUrl = thumbUrl;
        this.title = title;
        this.pubDate = pubDate;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> output = new HashMap<>();
        if(this.authors != null)
            output.put("authors", this.authors);
        if(this.canonical_url != null)
            output.put("canonical_url", this.canonical_url);
        if(this.section != null)
            output.put("section", this.section);
        if(this.tags != null)
            output.put("tags", this.tags);
        if(this.thumbUrl != null)
            output.put("thumbUrl", this.thumbUrl);
        if(this.title != null)
            output.put("title", this.title);
        if(this.pubDate != null)
            output.put("pub_date_tmsp", this.pubDate.getTimeInMillis());
        return output;
    }
}

