package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** 字段依据参考项目推断，未经真实样本验证。 */
class GoodsListItemJsonParserTest {
    private val parser = GoodsListItemJsonParser()

    @Test
    fun readsItemNameLogoAndDescriptionFallbacks() {
        val page = parser.parsePage(
            """{"code":200,"data":[
            {"item_id":"7","item_name":"磁吸充电器","item_description":"30W","item_logo":"http://img.example/a.png"},
            {"id":"8","title":"无图条目"}
            ]}""",
        )
        assertEquals(2, page.items.size)
        assertEquals("7", page.items[0].id)
        assertEquals("磁吸充电器", page.items[0].title)
        assertEquals("30W", page.items[0].subtitle)
        assertEquals("https://img.example/a.png", page.items[0].imageUrl)
        assertNull(page.items[1].imageUrl)
    }

    @Test
    fun emptyListStopsPagination() {
        assertFalse(parser.parsePage("""{"code":200,"data":[]}""").hasMore)
    }
}
