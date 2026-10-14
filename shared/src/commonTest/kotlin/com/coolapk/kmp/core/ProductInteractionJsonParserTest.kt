package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 点评与评分趋势的字段依据参考项目的类型定义；真实接口样本需后续抓取补充。 */
class ProductInteractionJsonParserTest {
    private val parser = ProductInteractionJsonParser()

    @Test
    fun readsRatingRowsWithScoreDeviceAndPictures() {
        val page = parser.parseRatings(
            """{"code":200,"data":[{"id":"7","uid":"42","username":"小明","userAvatar":"https://img.example/a.png",
            "message":"<p>续航很好</p>","rating_score":"4","device_title":"Pixel 9","dateline_text":"3天前",
            "picArr":["https://img.example/p1.png"]}]}""",
        )
        val rating = page.items.single()
        assertEquals("7", rating.id)
        assertEquals("小明", rating.username)
        assertEquals("续航很好", rating.message)
        assertEquals(4, rating.score)
        assertEquals("Pixel 9", rating.device)
        assertEquals(listOf("https://img.example/p1.png"), rating.pics)
        assertTrue(page.hasMore)
    }

    @Test
    fun emptyRatingPageStopsPagination() {
        val page = parser.parseRatings("""{"code":200,"data":[]}""")
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun outOfRangeScoreIsDropped() {
        val rating = parser.parseRatings(
            """{"code":200,"data":[{"id":"8","username":"a","message":"x","rating_score":"9"}]}""",
        ).items.single()
        assertNull(rating.score)
    }

    @Test
    fun readsChartPointsPerPeriodAndSkipsEmptyPoints() {
        val chart = parser.parseChart(
            """{"code":200,"data":{"week":{"ratingChart":{"x":[
            {"score":"4.5","count":"12","datelineStr":"10-01"},
            {"score":"0","count":"0","datelineStr":"10-02"},
            {"score":"3.8","count":"5","datelineStr":"10-08"}]}}}}""",
        )
        assertEquals(listOf("10-01", "10-08"), chart.series("week").map { it.label })
        assertEquals(4.5, chart.series("week").first().score)
        assertTrue(chart.series("day").isEmpty())
    }
}
