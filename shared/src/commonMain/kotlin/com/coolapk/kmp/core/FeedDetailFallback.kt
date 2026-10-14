package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 服务端人机验证下发的参数。`captchaField` 是令牌要写入的字段名。 */
data class CaptchaChallenge(
    val captchaId: String,
    val captchaField: String,
)

/**
 * 动态详情被人机验证拦截时的兜底策略。
 *
 * 对应 coolapk-desktop 的三层处理：正常请求 → 用易盾滑块换令牌后重试一次 →
 * 仍失败则改走酷安网页版入口（该入口带 XHR 指纹头时直接返回 JSON，可绕过接口风控）。
 * 这里只放纯逻辑，便于单元测试；网络调用由 Gateway 负责。
 */
object FeedDetailFallback {
    /** 酷安官方网易易盾 captchaId，服务端只返回 code=403 时使用该缺省值。 */
    const val DEFAULT_CAPTCHA_ID = "414e5c9b866a03db03f860a1a9101672"

    /** 易盾令牌的标准字段名。 */
    const val DEFAULT_CAPTCHA_FIELD = "_v2_post_token"

    private const val WEB_DETAIL_ORIGIN = "https://www.coolapk.com"

    /** 非 JSON 响应里用于识别验证码的文案标记，与原实现保持一致。 */
    private val CAPTCHA_TEXT_MARKERS = listOf(
        "err_request_captcha_v2",
        "当前访问需要验证码",
        "captchaId",
    )

    private val HEX = "0123456789ABCDEF"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 从详情接口的错误里解析验证码参数；不是验证码错误时返回 null。
     *
     * 服务端下发形式不固定：可能是顶层 `captchaId`，也可能把参数塞进
     * `messageExtra` / `extra` 字段的 JSON 字符串里；两者都没有时才退回
     * `code=403` + 文案判定。
     */
    fun captchaChallengeFrom(error: Throwable?): CaptchaChallenge? {
        val raw = error?.message?.trim().orEmpty()
        if (raw.isEmpty()) return null

        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        if (root != null) {
            fromEnvelope(root)?.let { return it }
            val reason = listOf("messageStatus", "message", "error")
                .mapNotNull { root.text(it) }
                .joinToString(" ")
            val isForbidden = root.text("code")?.toIntOrNull() == 403
            if (isForbidden && CAPTCHA_REGEX.containsMatchIn(reason)) {
                return CaptchaChallenge(DEFAULT_CAPTCHA_ID, DEFAULT_CAPTCHA_FIELD)
            }
            return null
        }

        // 兜底：UniFFI 包装或服务端直接返回纯文案时按标记识别。
        return if (CAPTCHA_TEXT_MARKERS.any { it in raw }) {
            CaptchaChallenge(DEFAULT_CAPTCHA_ID, DEFAULT_CAPTCHA_FIELD)
        } else {
            null
        }
    }

    /** 拼装官方标准的易盾令牌：`NEC:{captchaId前8位}:{validate}`。 */
    fun buildPostToken(captchaId: String, validate: String): String =
        "NEC:${captchaId.trim().take(8)}:${validate.trim()}"

    /** 酷安网页版动态页地址，作为验证码拦截后的只读兜底入口。 */
    fun webDetailUrl(feedId: String): String =
        "$WEB_DETAIL_ORIGIN/feed/${encodePathSegment(feedId.trim())}"

    /**
     * 从 `fetch_external_page` 的信封里取出网页版动态 JSON 原文。
     *
     * 酷安 `/feed/` 页面在 XHR 模式下返回 JSON，Rust 侧会把它原样放进
     * `data.html`（不做网页正文清洗），所以这里拿到的是可直接解析的 JSON 文本。
     */
    fun extractWebDetailJson(envelope: String): String {
        val root = runCatching { json.parseToJsonElement(envelope) as? JsonObject }.getOrNull()
            ?: error("网页版动态响应格式无效")
        val data = root["data"] as? JsonObject
            ?: error("网页版动态响应缺少数据")
        return data.text("html")?.takeIf(String::isNotBlank)
            ?: error("网页版动态响应缺少正文")
    }

    private fun fromEnvelope(root: JsonObject): CaptchaChallenge? {
        root.text("captchaId")?.let { id ->
            return CaptchaChallenge(id, root.text("captchaField") ?: DEFAULT_CAPTCHA_FIELD)
        }
        val extra = root.text("messageExtra") ?: root.text("extra") ?: return null
        if (!extra.startsWith("{")) return null
        val params = runCatching { json.parseToJsonElement(extra) as? JsonObject }.getOrNull()
            ?: return null
        val id = params.text("captchaId") ?: return null
        return CaptchaChallenge(id, params.text("captchaField") ?: DEFAULT_CAPTCHA_FIELD)
    }

    private val CAPTCHA_REGEX = Regex("captcha|验证码", RegexOption.IGNORE_CASE)

    /** 只保留路径段安全字符，避免动态 ID 里的异常字符改变请求路径。 */
    private fun encodePathSegment(value: String): String {
        val builder = StringBuilder(value.length)
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            val isUnreserved = (char in 'A'..'Z') || (char in 'a'..'z') ||
                (char in '0'..'9') || char in "-._~"
            if (isUnreserved) {
                builder.append(char)
            } else {
                builder.append('%').append(HEX[code shr 4]).append(HEX[code and 0xF])
            }
        }
        return builder.toString()
    }
}

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
