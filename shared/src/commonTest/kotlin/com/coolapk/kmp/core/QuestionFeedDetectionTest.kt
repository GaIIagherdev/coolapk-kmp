package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 问题动态识别规则与参考项目 isQuestionFeedEntity 一致。 */
class QuestionFeedDetectionTest {
    private val parser = FeedJsonParser()

    @Test
    fun questionEntityUsesItsOwnIdAsQuestionId() {
        val page = parser.parseHomePage(
            """{"code":200,"data":[{"id":"501","entityType":"feedQuestion","message":"怎么选手机"}]}""",
        )
        assertEquals("501", page.items[0].questionId)
    }

    @Test
    fun explicitQuestionIdWins() {
        val page = parser.parseHomePage(
            """{"code":200,"data":[{"id":"502","entityType":"question","questionId":"777"}]}""",
        )
        assertEquals("777", page.items[0].questionId)
    }

    @Test
    fun answersAndPlainFeedsAreNotQuestions() {
        val page = parser.parseHomePage(
            """{"code":200,"data":[{"id":"503","entityType":"answer","questionId":"9"},{"id":"504","entityType":"feed"}]}""",
        )
        assertNull(page.items[0].questionId)
        assertNull(page.items[1].questionId)
    }
}
