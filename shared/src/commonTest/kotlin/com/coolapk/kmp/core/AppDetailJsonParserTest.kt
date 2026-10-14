package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppDetailJsonParserTest {
    private val parser = AppDetailJsonParser()

    @Test
    fun parsesWrappedDetailAndScreenshots() {
        val detail = parser.parse(
            """
            {
              "code": 200,
              "data": {
                "title": "示例应用",
                "packageName": "app.example",
                "apkRomIcon": "http://image.coolapk.com/icon/a.png",
                "developername": "开发者",
                "apkversionname": "2.1",
                "apksize": "12 MB",
                "score": "8.7",
                "downCountFormatted": "20万",
                "description": "",
                "introduce": "功能介绍",
                "screenList": [
                  "//image.coolapk.com/screen/a.jpg",
                  {"url": "screen/b.jpg"}
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals("示例应用", detail?.name)
        assertEquals("app.example", detail?.packageName)
        assertEquals("https://image.coolapk.com/icon/a.png", detail?.iconUrl)
        assertEquals(8.7, detail?.score)
        assertEquals("功能介绍", detail?.description)
        assertEquals(
            listOf(
                "https://image.coolapk.com/screen/a.jpg",
                "https://image.coolapk.com/screen/b.jpg",
            ),
            detail?.screenshots,
        )
    }

    @Test
    fun rejectsPayloadWithoutAppName() {
        assertNull(parser.parse("""{"data":{"packageName":"app.example"}}"""))
    }
}
