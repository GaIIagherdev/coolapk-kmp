package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatJsonParserTest {
    private val parser = ChatJsonParser()

    @Test
    fun readsSessionsWithUnreadAndLastMessage() {
        val sessions = parser.parseSessions(
            """{"data":[{"ukey":"u-9","uid":"9","username":"乙","userAvatar":"//img.example/b.png",
            "message":"<p>你好</p>","dateline":"10-01","unreadCount":"3"}]}""",
        )
        val session = sessions.single()
        assertEquals("u-9", session.ukey)
        assertEquals("你好", session.lastMessage)
        assertEquals(3, session.unread)
        assertEquals("https://img.example/b.png", session.avatarUrl)
    }

    @Test
    fun readsHistoryWithCursorsAndStopsOnEmptyPage() {
        val page = parser.parseHistory(
            """{"data":[{"id":"1","uid":"9","username":"乙","message":"第一条"},{"id":"2","uid":"7","username":"甲","message":"第二条"}]}""",
        )
        assertEquals(listOf("第一条", "第二条"), page.items.map { it.text })
        assertEquals("1", page.firstItem)
        assertEquals("2", page.lastItem)
        assertTrue(page.hasMore)
        assertFalse(parser.parseHistory("""{"data":[]}""").hasMore)
    }
}
