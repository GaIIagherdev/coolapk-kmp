package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 字段依据参考项目 utils/myCards.ts 推断，未经真实样本验证。 */
class MyCardsJsonParserTest {
    private val parser = MyCardsJsonParser()

    @Test
    fun onlyFrequentGroupCardsAreKept() {
        val raw = """
            {"entities":[
              {"title":"常去","subTitle":"常用入口","entities":[
                {"entityId":"1","title":"浏览历史","url":"/member/hitHistoryList","logo":"http://image.coolapk.com/a.png"},
                {"entityId":"2","title":"我关注的话题","url":"/topic/myFollowTopicList"}
              ]},
              {"title":"其他分组","entities":[{"title":"不应显示","url":"/x"}]}
            ]}
        """.trimIndent()
        val cards = parser.parse(raw)
        assertEquals(listOf("浏览历史", "我关注的话题"), cards.map { it.title })
        assertEquals("https://image.coolapk.com/a.png", cards[0].imageUrl)
        assertNull(cards[1].imageUrl)
        assertEquals("/topic/myFollowTopicList", cards[1].url)
    }

    @Test
    fun fallsBackToFlatCardArrayWhenGroupTitleMissing() {
        val raw = """{"data":[{"title":"沙雕乐园","url":"/topic/1","pic":"https://img/1.png"}]}"""
        val cards = parser.parse(raw)
        assertEquals(1, cards.size)
        assertEquals("沙雕乐园", cards[0].title)
    }

    @Test
    fun embeddedJsonStringIsParsed() {
        val raw = """{"entities":"[{\"title\":\"常去\",\"entities\":[{\"title\":\"谷歌相机\",\"url\":\"/topic/2\"}]}]"}"""
        assertEquals(listOf("谷歌相机"), parser.parse(raw).map { it.title })
    }

    @Test
    fun invalidOrEmptyPayloadReturnsEmpty() {
        assertEquals(emptyList(), parser.parse("not json"))
        assertEquals(emptyList(), parser.parse("{}"))
    }

    @Test
    fun missingTitleGetsPlaceholder() {
        val raw = """{"entities":[{"title":"常去","entities":[{"url":"/x","pic":"a.png"}]}]}"""
        val card = parser.parse(raw).single()
        assertEquals("未命名卡片", card.title)
        assertEquals("https://image.coolapk.com/a.png", card.imageUrl)
    }
}
