package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TopicJsonParserTest {
    private val parser = TopicJsonParser()

    @Test
    fun parsesTopicHeaderWithoutInventingMissingStats() {
        val detail = parser.parseDetail(
            """{"code":200,"data":{"title":"摄影","logo":"//image.coolapk.com/topic.jpg",
              "description":"分享照片","view_num":1234,"commentnum":"56"}}""",
            "摄影",
        )

        assertEquals("摄影", detail.tag)
        assertEquals("摄影", detail.title)
        assertEquals("https://image.coolapk.com/topic.jpg", detail.logoUrl)
        assertEquals("分享照片", detail.description)
        assertEquals("1234", detail.viewCount)
        assertEquals("56", detail.discussionCount)
        assertEquals(null, detail.followerCount)
    }

    @Test
    fun rejectsMissingTopicData() {
        assertFailsWith<IllegalStateException> { parser.parseDetail("""{"code":200}""", "摄影") }
    }
}
