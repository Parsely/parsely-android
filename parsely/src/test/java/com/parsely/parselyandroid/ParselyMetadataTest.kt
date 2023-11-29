package com.parsely.parselyandroid

import java.util.Calendar
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ParselyMetadataTest {

    @Test
    fun `given metadata with complete set of data, when converting to map, then the map is as expected`() {
        // given
        val authors = arrayListOf("first author", "second author")
        val link = "sample link"
        val section = "sample section"
        val tags = arrayListOf("first tag", "second tag")
        val thumbUrl = "sample thumb url"
        val title = "sample title"
        val pubDate = Calendar.getInstance().apply { set(2023, 0, 1) }
        val sut = ParselyMetadata(
            authors,
            link,
            section,
            tags,
            thumbUrl,
            title,
            pubDate
        )

        // when
        val map = sut.toMap()

        // then
        assertThat(map).isEqualTo(
            mapOf(
                "authors" to authors,
                "link" to link,
                "section" to section,
                "tags" to tags,
                "thumb_url" to thumbUrl,
                "title" to title,
                "pub_date_tmsp" to pubDate.timeInMillis / 1000
            )
        )
    }
}
