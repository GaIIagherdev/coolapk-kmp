package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 浏览历史中的一条记录，字段与参考项目 HistoryPage 一致，未经真实样本验证。 */
data class HistoryEntry(
    val id: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    /** 可打开的动态 ID，从 url（/feed/数字）解析；无法解析时为 null。 */
    val feedId: String?,
    val dateline: String?,
)

data class HistoryEntryPage(
    val items: List<HistoryEntry>,
    val hasMore: Boolean,
    val firstItem: String? = null,
    val lastItem: String? = null,
)

class HistoryJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parsePage(raw: String): HistoryEntryPage {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return HistoryEntryPage(emptyList(), false)
        val rows = (root["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        // 与参考项目一致：只保留动态类历史（historyType 为 feed 或 feedarticle）。
        val items = rows.filter(::isFeedHistory).mapNotNull(::parseEntry)
        return HistoryEntryPage(
            items = items,
            hasMore = rows.isNotEmpty(),
            firstItem = rows.firstOrNull()?.text("entityId", "id"),
            lastItem = rows.lastOrNull()?.text("entityId", "id"),
        )
    }

    private fun isFeedHistory(row: JsonObject): Boolean {
        val type = row.text("historyType", "history_type")?.lowercase() ?: return true
        return type == "feed" || type == "feedarticle"
    }

    private fun parseEntry(row: JsonObject): HistoryEntry? {
        val url = row.text("url")
        val id = row.text("id", "entityId") ?: url ?: return null
        val title = row.text("title", "message") ?: return null
        return HistoryEntry(
            id = id,
            title = title,
            description = row.text("description", "subTitle", "summary"),
            imageUrl = row.text("logo", "pic")?.let(::normalizeImage),
            feedId = url?.let { FEED_URL.find(it)?.groupValues?.get(1) },
            dateline = row.text("dateline"),
        )
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun normalizeImage(value: String): String = when {
        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
        value.startsWith("http") -> value
        value.startsWith("//") -> "https:$value"
        else -> "https://image.coolapk.com/${value.trimStart('/')}"
    }

    private companion object {
        val FEED_URL = Regex("""/feed/(\d+)""")
    }
}
