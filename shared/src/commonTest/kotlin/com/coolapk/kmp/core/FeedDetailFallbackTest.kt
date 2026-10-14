package com.coolapk.kmp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FeedDetailFallbackTest {
    @Test
    fun readsCaptchaParamsFromTopLevelFields() {
        val challenge = FeedDetailFallback.captchaChallengeFrom(
            IllegalStateException(
                """{"code":403,"message":"需要验证","captchaId":"abc123def456","captchaField":"_v2_post_token"}""",
            ),
        )
        assertEquals(CaptchaChallenge("abc123def456", "_v2_post_token"), challenge)
    }

    @Test
    fun readsCaptchaParamsFromMessageExtraJsonString() {
        val challenge = FeedDetailFallback.captchaChallengeFrom(
            IllegalStateException(
                """{"code":403,"messageExtra":"{\"captchaId\":\"extra-id\",\"captchaField\":\"_v2_custom_token\"}"}""",
            ),
        )
        assertEquals(CaptchaChallenge("extra-id", "_v2_custom_token"), challenge)
    }

    @Test
    fun fallsBackToOfficialCaptchaIdWhenServerOnlyReturnsForbidden() {
        val challenge = FeedDetailFallback.captchaChallengeFrom(
            IllegalStateException("""{"code":403,"messageStatus":"err_request_captcha_v2"}"""),
        )
        assertEquals(FeedDetailFallback.DEFAULT_CAPTCHA_ID, challenge?.captchaId)
        assertEquals(FeedDetailFallback.DEFAULT_CAPTCHA_FIELD, challenge?.captchaField)
    }

    @Test
    fun ignoresNonCaptchaFailures() {
        assertNull(FeedDetailFallback.captchaChallengeFrom(IllegalStateException("""{"code":500,"message":"服务器开小差"}""")))
        assertNull(FeedDetailFallback.captchaChallengeFrom(IllegalStateException("动态详情加载失败：网络超时")))
        assertNull(FeedDetailFallback.captchaChallengeFrom(null))
    }

    @Test
    fun recognizesCaptchaMarkersInPlainText() {
        val challenge = FeedDetailFallback.captchaChallengeFrom(
            IllegalStateException("请求被拒绝：err_request_captcha_v2"),
        )
        assertEquals(FeedDetailFallback.DEFAULT_CAPTCHA_ID, challenge?.captchaId)
    }

    @Test
    fun buildsOfficialPostTokenFormat() {
        assertEquals(
            "NEC:414e5c9b:validated-token",
            FeedDetailFallback.buildPostToken("414e5c9b866a03db03f860a1a9101672", " validated-token "),
        )
    }

    @Test
    fun buildsWebDetailUrlAndEscapesId() {
        assertEquals(
            "https://www.coolapk.com/feed/12345678",
            FeedDetailFallback.webDetailUrl("12345678"),
        )
        assertEquals(
            "https://www.coolapk.com/feed/a%2Fb%3Fc",
            FeedDetailFallback.webDetailUrl("a/b?c"),
        )
    }

    @Test
    fun extractsWebDetailJsonFromEnvelope() {
        val envelope = """{"code":200,"data":{"title":"动态","html":"{\"data\":{\"id\":\"1\"}}","status":200}}"""
        assertEquals("""{"data":{"id":"1"}}""", FeedDetailFallback.extractWebDetailJson(envelope))
    }

    @Test
    fun rejectsMalformedWebDetailEnvelope() {
        assertFailsWith<IllegalStateException> {
            FeedDetailFallback.extractWebDetailJson("""{"code":200,"data":{"title":"动态"}}""")
        }
        assertFailsWith<IllegalStateException> {
            FeedDetailFallback.extractWebDetailJson("不是 JSON")
        }
    }
}
