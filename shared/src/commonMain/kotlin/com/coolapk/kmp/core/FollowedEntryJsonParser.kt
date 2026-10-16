package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 关注的论坛或话题中的一项。字段依据参考项目推断，未经真实样本验证。 */
data class FollowedEntry(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
)

data class FollowedEntryPage(
    val items: List<FollowedEntry>,
    val hasMore: Boolean,
)

class FollowedEntryJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parsePage(raw: String): FollowedEntryPage {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return FollowedEntryPage(emptyList(), false)
        val rows = (root["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val items = rows.mapNotNull { row ->
            val title = row.text("title", "name", "tag", "tagName") ?: return@mapNotNull null
            val id = row.text("id", "tagId", "nodeId", "entityId") ?: title
            FollowedEntry(
                id = id,
                title = title,
                subtitle = row.text("description", "subTitle", "subtitle", "summary", "intro"),
                imageUrl = row.text("logo", "pic", "image", "cover")?.let(::normalizeImage),
            )
        }
        return FollowedEntryPage(items = items, hasMore = rows.isNotEmpty())
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun normalizeImage(value: String): String = when {
        value.startsWith("http") -> value
        value.startsWith("//") -> "https:$value"
        else -> "https://image.coolapk.com/${value.trimStart('/')}"
    }
}
