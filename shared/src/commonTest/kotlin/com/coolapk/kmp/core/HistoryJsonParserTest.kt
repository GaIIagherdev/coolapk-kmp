package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryJsonParserTest {
    private val parser = HistoryJsonParser()

    @Test
    fun keepsFeedHistoryRowsAndExtractsFeedId() {
        val page = parser.parsePage(
            """{"code":200,"data":[
            {"id":"feed:88","historyType":"feed","title":"测试帖子","description":"摘要","logo":"http://img.example/a.png","url":"/feed/88","dateline":"1786022084"},
            {"id":"topic:1","historyType":"topic","title":"某话题","url":"/topic/1"}
            ]}""",
        )
        assertEquals(1, page.items.size)
        assertEquals("测试帖子", page.items[0].title)
        assertEquals("摘要", page.items[0].description)
        assertEquals("https://img.example/a.png", page.items[0].imageUrl)
        assertEquals("88", page.items[0].feedId)
        assertTrue(page.hasMore)
    }

    @Test
    fun rowWithoutFeedUrlStillShowsWithoutTarget() {
        val page = parser.parsePage("""{"code":200,"data":[{"id":"9","historyType":"feed","title":"无链接"}]}""")
        assertNull(page.items[0].feedId)
    }

    @Test
    fun emptyListStopsPagination() {
        val page = parser.parsePage("""{"code":200,"data":[]}""")
        assertEquals(false, page.hasMore)
    }
}
