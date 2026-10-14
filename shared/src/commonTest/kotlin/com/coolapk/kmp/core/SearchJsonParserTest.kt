package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchJsonParserTest {
    private val parser = SearchJsonParser()

    @Test
    fun parsesMixedCardEntitiesAndServerCursor() {
        val page = parser.parsePage(
            """{"firstItem":"cursor-first","lastItem":"cursor-last","data":[
              {"entityType":"card","entities":[
                {"entityType":"apk","id":"12","title":"应用甲","packageName":"app.one","icon":"//image.coolapk.com/icon.png"},
                {"entityType":"feed","id":"13","message":"动态内容","username":"作者"},
                {"entityType":"user","uid":"14","username":"用户乙","userAvatar":"http://image.coolapk.com/avatar.png"}]},
              {"entityType":"sponsorForSearch","id":"ad","title":"广告"}
            ]}""",
            SearchCategory.ALL,
        )
        assertEquals(3, page.items.size)
        assertEquals(listOf(SearchResultKind.APP, SearchResultKind.FEED, SearchResultKind.USER), page.items.map { it.kind })
        assertEquals("app.one", page.items[0].targetId)
        assertEquals("13", page.items[1].targetId)
        assertEquals("14", page.items[2].targetId)
        assertEquals("https://image.coolapk.com/icon.png", page.items[0].imageUrl)
        assertEquals("cursor-first", page.firstItem)
        assertEquals("cursor-last", page.lastItem)
        assertTrue(page.hasMore)
    }

    @Test
    fun parsesCategoryFallbackAndNoMore() {
        val page = parser.parsePage(
            """{"data":{"noMore":true,"list":[
              {"id":"product-1","title":"手机甲","logo":"/device.png"},
              {"id":"product-2","title":"手机乙"}
            ]}}""",
            SearchCategory.PRODUCT,
        )
        assertEquals(SearchResultKind.PRODUCT, page.items.first().kind)
        assertEquals("product-1", page.items.first().targetId)
        assertEquals("https://image.coolapk.com/device.png", page.items.first().imageUrl)
        assertFalse(page.hasMore)
        assertEquals("product-1", page.firstItem)
        assertEquals("product-2", page.lastItem)
    }

    @Test
    fun readsNestedDataObject() {
        val page = parser.parsePage(
            """{"data":{"data":{"entities":[{"entityType":"apk","id":"5","title":"应用乙","packageName":"app.two"}]}}}""",
            SearchCategory.APP,
        )
        assertEquals("app.two", page.items.single().targetId)
    }

    @Test
    fun resolvesUserUidFromNestedInfoAndTopicTagFromUrl() {
        val page = parser.parsePage(
            """{"data":[
              {"entityType":"user","title":"用户乙","userInfo":{"uid":"42"}},
              {"entityType":"feedTopic","title":"摄影话题","url":"https://www.coolapk.com/topic/%E6%91%84%E5%BD%B1"}
            ]}""",
            SearchCategory.ALL,
        )
        assertEquals("42", page.items[0].targetId)
        assertEquals("摄影", page.items[1].targetId)
    }

    @Test
    fun questionDoesNotClaimFeedDetailRoute() {
        val page = parser.parsePage(
            """{"data":[{"entityType":"feedQuestion","questionId":"q1","title":"为什么？"}]}""",
            SearchCategory.ASK,
        )
        assertEquals(SearchResultKind.QUESTION, page.items.single().kind)
        assertNull(page.items.single().targetId)
    }

    @Test
    fun extractsHotWordsWithoutSponsoredRows() {
        val words = parser.parseHotWords(
            """{"data":[{"entityTemplate":"searchHotCard","entities":[
              {"title":"新手机"},{"searchValue":"热门应用"},
              {"title":"新手机"},{"entityType":"sponsorForSearch","title":"推广"}
            ]}]}""",
        )
        assertEquals(listOf("新手机", "热门应用"), words)
    }
}
