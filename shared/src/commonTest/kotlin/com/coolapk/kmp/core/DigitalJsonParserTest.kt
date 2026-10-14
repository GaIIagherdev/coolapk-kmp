package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DigitalJsonParserTest {
    private val parser = DigitalJsonParser()

    @Test
    fun readsVisibleDigitalTabsAndNestedSubchannels() {
        val config = parser.parseConfig(
            """
            {"data":{"extraData":"{\"selectedHomeTab\":\"V10_DIGITAL_HOME\"}","configPages":[
              {"entityType":"ConfigPage","title":"其他栏目","pageName":"V9_HOME_TAB_HEADLINE"},
              {"entityType":"ConfigPage","title":"隐藏","pageName":"V10_DIGITAL_HIDDEN","visibility":"hidden"},
              {"entityType":"ConfigPage","title":"数码首页","pageName":"V10_DIGITAL_HOME","url":"V10_DIGITAL_HOME","order":2,
               "entities":"[{\"title\":\"手机\",\"pageName\":\"V10_DIGITAL_PHONE\",\"requestParams\":{\"type\":\"phone\"}}]"},
              {"entityType":"ConfigPage","title":"数码库","pageName":"V10_PRODUCT_LIST","url":"/product/categoryList","order":1}
            ]}}
            """.trimIndent(),
        )

        assertEquals(listOf("数码库", "数码首页"), config.tabs.map(DigitalTab::title))
        assertTrue(config.tabs.first().category)
        assertEquals("V10_DIGITAL_HOME", config.selectedKey)
        assertEquals("手机", config.tabs.last().entities.first().title)
        assertEquals("phone", config.tabs.last().entities.first().requestArgs["type"].toString().trim('"'))
    }

    @Test
    fun retainsServerTemplatesChildrenFeedsAndOuterCursor() {
        val page = parser.parsePage(
            """
            {"data":[
              {"entityId":"config","entityTemplate":"configCard","extraData":{"pageContext":"next"}},
              {"entityId":"outer","entityType":"card","entities":[
                {"entityId":"banner","entityTemplate":"imageCarouselCard_1","title":"推荐","entities":[
                  {"entityId":"product-7","entityType":"product","title":"新手机","logo":"//image.coolapk.com/phone.png"}
                ]},
                {"id":"feed-1","entityType":"feed","message":"动态内容","username":"酷友"}
              ]}
            ],"hasMore":false}
            """.trimIndent(),
        )

        assertEquals(listOf("banner", "feed-1"), page.items.map(DigitalEntity::id))
        assertEquals("imageCarouselCard_1", page.items.first().entityTemplate)
        assertEquals("https://image.coolapk.com/phone.png", page.items.first().entities.first().imageUrl)
        assertEquals("feed-1", assertNotNull(page.items.last().feed).id)
        assertEquals("config", page.firstItem)
        assertEquals("outer", page.lastItem)
        assertEquals("next", page.pageContext)
        assertFalse(page.hasMore)
    }

    @Test
    fun productListKeepsGroupMarkersAndExplicitPagination() {
        val page = parser.parsePage(
            """
            {"data":[
              {"entityTemplate":"productGroupTitle","title":"旗舰系列"},
              {"id":91,"entityType":"product","title":"旗舰手机","product_specs":["高刷"]}
            ],"pagination":{"firstItem":"start","lastItem":"end","hasMore":true}}
            """.trimIndent(),
        )

        assertEquals("productGroupTitle", page.items.first().entityTemplate)
        assertEquals("旗舰手机", page.items.last().title)
        assertEquals("start", page.firstItem)
        assertEquals("end", page.lastItem)
        assertTrue(page.hasMore)
    }
}
