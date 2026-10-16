package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 字段依据参考项目推断，未经真实样本验证。 */
class ListEntryJsonParserTest {
    private val parser = ListEntryJsonParser()

    @Test
    fun readsEntriesWithFallbackFieldNames() {
        val page = parser.parsePage(
            """{"code":200,"data":[
            {"id":"3","title":"数码频道","description":"手机与平板","logo":"/logo/a.png"},
            {"tag":"骁龙8至尊版","subTitle":"旗舰芯片讨论"},
            {"name":"无图论坛","id":"9"}
            ]}""",
        )
        assertEquals(3, page.items.size)
        assertEquals("3", page.items[0].id)
        assertEquals("手机与平板", page.items[0].subtitle)
        assertEquals("https://image.coolapk.com/logo/a.png", page.items[0].imageUrl)
        assertEquals("骁龙8至尊版", page.items[1].id)
        assertEquals("旗舰芯片讨论", page.items[1].subtitle)
        assertNull(page.items[1].imageUrl)
        assertEquals("9", page.items[2].id)
        assertTrue(page.hasMore)
    }

    @Test
    fun skipsRowsWithoutTitleAndStopsOnEmptyList() {
        val page = parser.parsePage("""{"code":200,"data":[{"id":"1"},{"title":"保留"}]}""")
        assertEquals(listOf("保留"), page.items.map { it.title })
        val empty = parser.parsePage("""{"code":200,"data":[]}""")
        assertFalse(empty.hasMore)
    }
}
