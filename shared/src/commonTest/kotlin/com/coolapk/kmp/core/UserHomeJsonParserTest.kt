package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserHomeJsonParserTest {
    private val parser = UserHomeJsonParser()

    @Test
    fun classifiesUserTopicAndSkipsEmptyRows() {
        val rows = parser.parseRows(
            """{"data":{"homeTabCardRows":[
            {"title":"关注的人","subTitle":"最近","entities":[{"uid":"7","userName":"甲","userAvatar":"//img.example/a.png"}]},
            {"title":"关注的板块","entities":[{"id":"tag1","title":"手机","entityType":"topic","pic":"https://img.example/t.png"}]},
            {"title":"空卡片","entities":[]}
            ]}}""",
        )
        assertEquals(2, rows.size)
        assertEquals(HomeCardKind.USER, rows[0].kind)
        assertEquals("关注的人", rows[0].title)
        assertEquals("甲", rows[0].entities.single().title)
        assertEquals("https://img.example/a.png", rows[0].entities.single().imageUrl)
        assertEquals(HomeCardKind.TOPIC, rows[1].kind)
        assertTrue(rows.none { it.title == "空卡片" })
    }
}
