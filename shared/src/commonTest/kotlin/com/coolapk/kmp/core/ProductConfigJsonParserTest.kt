package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ProductConfigJsonParserTest {
    private val parser = ProductConfigJsonParser()

    @Test
    fun buildsBasicAndGroupedSectionsAcrossModels() {
        val a = parser.parse(
            """{"data":{"title":"A版","cpu":"骁龙8","ram":"12GB","config_data":"{\"屏幕\":{\"尺寸\":\"6.3\"}}"}}""", "1",
        )!!
        val b = parser.parse(
            """{"data":{"title":"B版","cpu":"骁龙8","ram":"16GB","config_data":"{\"屏幕\":{\"尺寸\":\"6.7\"},\"相机\":{\"主摄\":\"50MP\"}}"}}""", "2",
        )!!
        val sections = parser.buildSections(listOf(a, b))
        val basic = sections.first { it.title == "基础信息" }
        assertEquals(listOf("处理器", "运行内存"), basic.rows.map { it.label })
        assertEquals(listOf("12GB", "16GB"), basic.rows.first { it.key == "ram" }.values)
        val screen = sections.first { it.title == "屏幕" }
        assertEquals(listOf("6.3", "6.7"), screen.rows.single().values)
        assertEquals(listOf("", "50MP"), sections.first { it.title == "相机" }.rows.single().values)
    }
}
