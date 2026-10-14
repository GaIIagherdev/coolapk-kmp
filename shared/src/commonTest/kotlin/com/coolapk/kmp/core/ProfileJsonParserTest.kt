package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** 字段依据参考项目 UserPage 的取值规则；真实样本需后续抓取补充。 */
class ProfileJsonParserTest {
    private val parser = ProfileJsonParser(currentYear = { 2026 })

    @Test
    fun readsDesktopProfileFieldsWithNestedUserInfoFallback() {
        val profile = parser.parse(
            """{"data":{"uid":"42","username":"酷友","userAvatar":"//img.example/a.png","bio":"签名",
            "cover":"//img.example/cover.jpg","level":"7","exp":"120","maxExp":"200","be_like_num":"1234",
            "userInfo":{"gender":"1","birthyear":"1995","birthmonth":"3","birthday":"2","city":"上海",
            "ip_location":"广东","logintime":"1790000000","regdate":"1600000000","verify_title":"认证开发者",
            "isFollow":"1","isFans":"0","product_owner_count":"5","goods_count":"3"}}}""",
        )!!
        assertEquals("https://img.example/cover.jpg", profile.cover)
        assertEquals(7, profile.level)
        assertEquals(120, profile.exp)
        assertEquals(200, profile.maxExp)
        assertEquals(1234, profile.likeCount)
        assertEquals(1, profile.gender)
        assertEquals("95后", profile.ageTag)
        assertEquals("双鱼座", profile.astro)
        assertEquals("1995年3月2日 (31岁)", profile.birthLabel)
        assertEquals("上海", profile.city)
        assertEquals("广东", profile.ipLocation)
        assertEquals(1790000000L, profile.loginTime)
        assertEquals(1600000000L, profile.regDate)
        assertEquals("认证开发者", profile.verifyTitle)
        assertTrue(profile.isFollow)
        assertFalse(profile.isFans)
        assertEquals(5, profile.productOwnerCount)
        assertEquals(3, profile.goodsCount)
    }

    @Test
    fun hidesUnknownLocationsAndMissingBirthFields() {
        val profile = parser.parse(
            """{"data":{"uid":"43","username":"a","city":"保密","ip_location":"未知"}}""",
        )!!
        assertNull(profile.city)
        assertNull(profile.ipLocation)
        assertNull(profile.ageTag)
        assertNull(profile.astro)
        assertNull(profile.birthLabel)
    }
}
