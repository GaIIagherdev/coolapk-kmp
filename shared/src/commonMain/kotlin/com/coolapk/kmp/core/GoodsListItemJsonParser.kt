package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 好物清单（万物清单）中的条目，字段与参考项目 AnyListDetailPage 的取值顺序一致。
 * 未经真实样本验证。
 */
class GoodsListItemJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parsePage(raw: String): ListEntryPage {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return ListEntryPage(emptyList(), false)
        val rows = (root["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val items = rows.mapNotNull { row ->
            val title = row.text("item_name", "title", "name") ?: return@mapNotNull null
            val id = row.text("item_id", "id") ?: title
            ListEntry(
                id = id,
                title = title,
                subtitle = row.text("item_description", "description"),
                imageUrl = row.text("item_logo", "pic", "logo")?.let(::normalizeImage),
            )
        }
        return ListEntryPage(items = items, hasMore = rows.isNotEmpty())
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
}
