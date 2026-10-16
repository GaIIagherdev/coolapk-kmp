package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 收藏单（收藏夹）。字段依据参考项目类型定义推断，未经真实样本验证。 */
data class FeedCollection(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String?,
    val itemCount: Int,
    val followCount: Int,
    val likeCount: Int,
    val isOpen: Boolean,
    val isDefault: Boolean,
    val ownerUid: String?,
)

data class FeedCollectionPage(
    val items: List<FeedCollection>,
    val hasMore: Boolean,
    /** 分页游标：首尾条目的 entityId，下一页需要原样回传。 */
    val firstItem: String? = null,
    val lastItem: String? = null,
)

class CollectionJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    /** 收藏单列表；Rust 层已把行规范化为 id / title / cover 等字段。 */
    fun parsePage(raw: String): FeedCollectionPage {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return FeedCollectionPage(emptyList(), false)
        val rows = (root["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val items = rows.mapNotNull(::parseCollection)
        return FeedCollectionPage(
            items = items,
            hasMore = rows.isNotEmpty(),
            firstItem = rows.firstOrNull()?.text("entityId", "id"),
            lastItem = rows.lastOrNull()?.text("entityId", "id"),
        )
    }

    /** 收藏单详情；返回结构与列表行一致，取不到 id 时回退为请求时使用的 ID。 */
    fun parseDetail(raw: String, collectionId: String): FeedCollection? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val data = root["data"] as? JsonObject ?: return null
        val withId = if ("id" in data) data else JsonObject(data + ("id" to JsonPrimitive(collectionId)))
        return parseCollection(withId)
    }

    private fun parseCollection(row: JsonObject): FeedCollection? {
        val id = row.text("id", "entityId", "collectionId") ?: return null
        val title = row.text("title", "name") ?: return null
        return FeedCollection(
            id = id,
            title = title,
            description = row.text("description", "summary").orEmpty(),
            coverUrl = row.text("cover", "coverPic", "pic")?.let(::normalizeImage),
            itemCount = row.int("itemNum", "item_num", "feedNum", "count"),
            followCount = row.int("follownum", "followNum", "follow_num"),
            likeCount = row.int("favnum", "likeNum", "like_num"),
            isOpen = row.flag("isOpen", "is_open", "open") ?: true,
            isDefault = row.flag("defaultCollected", "default_collected", "isDefault", "is_default") ?: false,
            ownerUid = row.text("uid", "userId", "user_id"),
        )
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun JsonObject.int(vararg keys: String): Int = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    } ?: 0

    private fun JsonObject.flag(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
        val element: JsonElement = this[key] ?: return@firstNotNullOfOrNull null
        val value = (element as? JsonPrimitive)?.contentOrNull ?: return@firstNotNullOfOrNull null
        value == "1" || value == "true"
    }

    private fun normalizeImage(value: String): String = when {
        value.startsWith("http") -> value
        value.startsWith("//") -> "https:$value"
        else -> "https://image.coolapk.com/${value.trimStart('/')}"
    }
}
