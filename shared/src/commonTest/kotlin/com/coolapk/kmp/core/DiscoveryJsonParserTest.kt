package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiscoveryJsonParserTest {
    private val parser = DiscoveryJsonParser()

    @Test
    fun readsVisibleServerTabsInConfiguredOrder() {
        val config = parser.parseConfig(
            """
            {"data":[
              {"id":1,"title":"首页","entities":[{"title":"不应出现","page_name":"V9_HOME"}]},
              {"id":20131,"title":"发现","extraDataArr":{"selectedHomeTab":"V11_FIND_LIFE"},"entities":[
                {"entityType":"ConfigPage","title":"生活","page_name":"V11_FIND_LIFE","url":"V11_FIND_LIFE","order":2},
                {"entityType":"ConfigPage","title":"隐藏","url":"V11_FIND_HIDDEN","status":0},
                {"entityType":"ConfigPage","title":"酷图","url":"V11_FIND_COOLPIC","order":1,"tabIcon":"//image.coolapk.com/icon.png"}
              ]}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("酷图", "生活"), config.tabs.map(DiscoveryTab::title))
        assertEquals("V11_FIND_LIFE", config.selectedKey)
        assertEquals("https://image.coolapk.com/icon.png", config.tabs.first().iconUrl)
    }

    @Test
    fun keepsMixedCardsAndPageContext() {
        val page = parser.parsePage(
            """
            {"data":[
              {"entityId":"config","entityType":"card","entityTemplate":"configCard","extraData":{"pageContext":"next-context"}},
              {"entityId":"card-1","entityType":"topic","title":"热点话题","pic":"topic.png"},
              {"id":"feed-1","entityId":"cursor-1","entityType":"feed","message":"动态内容","username":"酷友"}
            ],"firstItem":"start","lastItem":"end","hasMore":false}
            """.trimIndent(),
        )

        assertEquals(listOf("card-1", "cursor-1"), page.cards.map(DiscoveryCard::id))
        assertEquals("热点话题", page.cards.first().title)
        assertEquals("https://image.coolapk.com/topic.png", page.cards.first().imageUrl)
        assertEquals("feed-1", assertNotNull(page.cards.last().feed).id)
        assertEquals("start", page.firstItem)
        assertEquals("end", page.lastItem)
        assertEquals("next-context", page.pageContext)
        assertFalse(page.hasMore)
    }

    @Test
    fun emptyConfigCardDoesNotStartUnboundedPagination() {
        val page = parser.parsePage(
            """{"data":[{"entityType":"card","entityTemplate":"configCard","entityId":"only"},
                {"entityType":"card","entityTemplate":"sponsorCard","entityId":"ad"}]}""",
        )

        assertTrue(page.cards.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun expandsUntemplatedGroupButKeepsOuterCursor() {
        val page = parser.parsePage(
            """
            {"data":[{"entityId":"group-cursor","entityType":"card","entities":[
              {"entityId":"feed-1","entityType":"feed","message":"正文"},
              {"entityId":"topic-1","entityType":"topic","title":"话题"}
            ]}]}
            """.trimIndent(),
        )

        assertEquals(listOf("feed-1", "topic-1"), page.cards.map(DiscoveryCard::id))
        assertEquals("group-cursor", page.firstItem)
        assertEquals("group-cursor", page.lastItem)
        assertNotNull(page.cards.first().feed)
    }

    @Test
    fun readsImageCarouselCaptionAndExpandsSelectorLinks() {
        val page = parser.parsePage(
            """
            {"data":[
              {"entityId":41629,"entityType":"card","entityTemplate":"imageCarouselCard_1",
               "title":"","url":"","pic":"http://image.coolapk.com/banner.jpg","entities":[
                 {"entityType":"image_1","pic":"http://image.coolapk.com/tile.jpg",
                  "title":"户外兴趣小组","url":"/t/户外兴趣小组"}
               ]},
              {"entityId":41630,"entityType":"card","entityTemplate":"selectorLinkCard",
               "title":"","url":"","entities":[
                 {"entityType":"selectorLink","title":"热议","url":"#/feed/digestList?filterId=82"},
                 {"entityType":"selectorLink","title":"兴趣爱好","url":"#/feed/digestList?filterId=78"}
               ]}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("户外兴趣小组", "热议", "兴趣爱好"), page.cards.map(DiscoveryCard::title))
        assertEquals("https://image.coolapk.com/banner.jpg", page.cards.first().imageUrl)
        assertEquals("/t/户外兴趣小组", page.cards.first().url)
        assertEquals("#/feed/digestList?filterId=78", page.cards.last().url)
        assertEquals("41629", page.firstItem)
        assertEquals("41630", page.lastItem)
    }
}
