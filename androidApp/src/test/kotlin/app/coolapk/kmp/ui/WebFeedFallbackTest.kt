package app.coolapk.kmp.ui

import com.coolapk.kmp.core.FeedDetailFallback
import com.coolapk.kmp.core.FeedDetailJsonParser
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网页版兜底的回归测试。
 *
 * 夹具是 `https://www.coolapk.com/feed/12345678` 在带 `X-Requested-With` /
 * `X-App-Id` 指纹头时返回的真实响应体（酷安该入口在 XHR 模式下直接返回 JSON）。
 * 这里复刻 `fetch_external_page` 在 Rust 侧包出的信封，验证整条解析链路。
 */
class WebFeedFallbackTest {
    private fun fixture(): String =
        checkNotNull(javaClass.getResourceAsStream("/web_feed_page_response.json")) {
            "缺少测试夹具 web_feed_page_response.json"
        }.use { it.readBytes().decodeToString() }

    @Test
    fun parsesRealWebFeedPageResponse() {
        val body = fixture()

        // 与 rust-core 的 json!({"code":200,"data":{...}}) 保持一致
        val envelope = buildJsonObject {
            put("code", 200)
            put(
                "data",
                buildJsonObject {
                    put("title", "外部链接")
                    put("html", body)
                    put("status", 200)
                },
            )
        }.toString()

        val raw = FeedDetailFallback.extractWebDetailJson(envelope)
        assertEquals(body, raw)

        val item = FeedDetailJsonParser().parseDetail(raw)
        assertEquals("12345678", item.id)
        assertTrue("正文不应为空", item.message.isNotBlank())
        assertTrue("作者不应为空", item.username.isNotBlank())
    }

    /**
     * 动态不可见时网页版入口不返回 `data`，解析器只能给笼统失败，
     * 因此网关必须优先采用服务端自己的说明。
     */
    @Test
    fun unavailableFeedSurfacesServerReason() {
        val body = checkNotNull(javaClass.getResourceAsStream("/web_feed_unavailable_response.json")) {
            "缺少测试夹具 web_feed_unavailable_response.json"
        }.use { it.readBytes().decodeToString() }

        assertEquals(
            "作者设置为仅半年内动态可见，无法查看",
            FeedDetailFallback.webDetailServerMessage(body),
        )
        assertThrows(IllegalStateException::class.java) { FeedDetailJsonParser().parseDetail(body) }
    }
}
