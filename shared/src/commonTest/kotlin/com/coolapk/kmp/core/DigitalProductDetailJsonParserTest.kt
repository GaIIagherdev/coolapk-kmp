package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DigitalProductDetailJsonParserTest {
    private val parser = DigitalProductDetailJsonParser()

    @Test
    fun readsOfficialProductFieldsAndImages() {
        val detail = assertNotNull(parser.parse(
            """
            {"code":200,"data":{"id":42,"index_title":"旗舰手机","product_logo":"//image.coolapk.com/logo.png",
             "device_info":"产品概述","brand_name":"品牌","category_name":"手机",
             "owner_star_average_score":"9.2","owner_rating_total_num":35,"hot_num_txt":"1.2万",
             "product_specs":["处理器","高刷屏"],"imageUriList":[{"sourceUrl":"http://image.coolapk.com/a.jpg"}]}}
            """.trimIndent(),
        ))
        assertEquals("42", detail.id)
        assertEquals("旗舰手机", detail.title)
        assertEquals("https://image.coolapk.com/logo.png", detail.logoUrl)
        assertEquals("产品概述", detail.description)
        assertEquals("品牌", detail.brand)
        assertEquals("手机", detail.category)
        assertEquals(9.2, detail.score)
        assertEquals(35, detail.ratingCount)
        assertEquals("1.2万", detail.hotCount)
        assertEquals(listOf("处理器", "高刷屏"), detail.specs)
        assertEquals(listOf("https://image.coolapk.com/a.jpg"), detail.imageUrls)
    }

    @Test
    fun rejectsMissingProductPayload() {
        assertNull(parser.parse("""{"code":200,"data":null}"""))
    }
}
