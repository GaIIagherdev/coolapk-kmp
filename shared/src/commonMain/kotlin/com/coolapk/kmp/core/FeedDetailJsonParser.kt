package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class ReplySort(val apiValue: String) {
    LATEST("lastupdate_desc"),
    NEWEST("dateline_desc"),
    POPULAR("popular"),
}

data class ReplyPreview(
    val username: String,
    val message: String,
)

data class ReplyItem(
    val id: String,
    val feedId: String,
    val parentId: String?,
    val username: String,
    val avatarUrl: String?,
    val replyToUsername: String?,
    val message: String,
    val createdAt: String?,
    val floor: String?,
    val likeCount: Int,
    val replyCount: Int,
    val isLiked: Boolean,
    val isFeedAuthor: Boolean,
    val imageUrls: List<String>,
    val previewReplies: List<ReplyPreview>,
    val uid: String? = null,
)

data class ReplyPage(
    val items: List<ReplyItem>,
    val hasMore: Boolean = items.isNotEmpty(),
    val lastItem: String? = items.lastOrNull()?.id,
)

class FeedDetailJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val feedParser: FeedJsonParser = FeedJsonParser(),
) {
    fun parseDetail(raw: String): FeedItem {
        feedParser.parseHomePage(raw).items.firstOrNull()?.let { return it }
        val root = json.parseToJsonElement(raw)
        val item = root.findDetailEntity()
            ?: throw IllegalStateException("动态详情缺少有效内容")
        return feedParser.parseHomePage(JsonObject(mapOf("data" to item)).toString())
            .items.firstOrNull()
            ?: throw IllegalStateException("动态详情缺少有效内容")
    }

    fun parseReplies(raw: String): ReplyPage {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: return ReplyPage(emptyList(), hasMore = false)
        val payload = root["data"] ?: return ReplyPage(emptyList(), hasMore = false)
        val rows = payload.replyRows()
        val replies = rows.mapNotNull(::parseReply).distinctBy(ReplyItem::id)
        val explicitHasMore = root.boolean("hasMore") ?: root.boolean("has_more")
            ?: (payload as? JsonObject)?.boolean("hasMore")
            ?: (payload as? JsonObject)?.boolean("has_more")
        return ReplyPage(
            items = replies,
            hasMore = explicitHasMore ?: rows.isNotEmpty(),
        )
    }

    private fun parseReply(row: JsonObject): ReplyItem? {
        val id = row.string("id") ?: row.string("entityId") ?: return null
        val user = (row["userInfo"] ?: row["user"]) as? JsonObject
        val previews = (row["replyRows"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { child ->
                val message = child.string("message") ?: child.string("description")
                message?.let {
                    ReplyPreview(
                        username = child.string("username")
                            ?: (child["userInfo"] as? JsonObject)?.string("username")
                            ?: "酷友",
                        message = it,
                    )
                }
            }
        val pictures = sequenceOf("picArr", "images")
            .mapNotNull { key ->
                (row[key] as? JsonArray)?.mapNotNull { it.imageUrl() }
                    ?.takeIf(List<String>::isNotEmpty)
            }
            .firstOrNull()
            ?: listOfNotNull(row.string("pic")?.normalizeReplyImageUrl())
        return ReplyItem(
            id = id,
            feedId = row.string("fid").orEmpty(),
            parentId = row.string("rid")?.takeIf { it != "0" },
            username = row.string("username") ?: user?.string("username") ?: "酷友",
            avatarUrl = (row.string("userAvatar") ?: user?.string("userAvatar"))
                ?.normalizeReplyImageUrl(),
            replyToUsername = (row.string("replyUsername") ?: row.string("rusername"))
                ?.takeIf(String::isNotBlank),
            message = row.string("message") ?: row.string("description") ?: "",
            createdAt = row.string("dateline"),
            floor = row.string("floor")?.takeIf { it != "0" },
            likeCount = row.int("likenum") ?: 0,
            replyCount = maxOf(row.int("replyRowsCount") ?: 0, row.int("replynum") ?: 0, previews.size),
            isLiked = (row["userAction"] as? JsonObject)?.boolean("like") ?: false,
            isFeedAuthor = row.boolean("isFeedAuthor") ?: false,
            imageUrls = pictures,
            previewReplies = previews,
            uid = row.string("uid") ?: row.string("userId") ?: row.string("user_id")
                ?: user?.string("uid") ?: user?.string("userId") ?: user?.string("id"),
        )
    }

    private fun JsonElement.findDetailEntity(): JsonObject? = when (this) {
        is JsonObject -> {
            if ((string("id") ?: string("entityId")) != null &&
                (string("message") != null || string("messageRaw") != null ||
                    string("entityType") in setOf("feed", "article", "news", "question", "answer") ||
                    string("entityType")?.startsWith("feed_") == true)
            ) this
            else sequenceOf("data", "detail", "feed", "entity", "row")
                .mapNotNull { key -> this[key]?.findDetailEntity() }
                .firstOrNull()
        }
        is JsonArray -> firstNotNullOfOrNull { it.findDetailEntity() }
        else -> null
    }

    private fun JsonElement.replyRows(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> sequenceOf("list", "rows", "data", "entities")
            .mapNotNull { key -> this[key]?.replyRows() }
            .firstOrNull()
            ?: emptyList()
        else -> emptyList()
    }

    private fun JsonElement.imageUrl(): String? = when (this) {
        is JsonObject -> sequenceOf("sourceUrl", "source_url", "url", "pic", "imageUrl")
            .mapNotNull { string(it) }
            .firstOrNull()
            ?.normalizeReplyImageUrl()
        is JsonPrimitive -> contentOrNull?.normalizeReplyImageUrl()
        else -> null
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

    private fun JsonObject.boolean(key: String): Boolean? = when (string(key)?.lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }
}

private fun String.normalizeReplyImageUrl(): String? {
    val value = trim()
    if (value.isEmpty() || value == "null" || value == "undefined") return null
    return when {
        value.startsWith("http://", ignoreCase = true) -> "https://${value.substring(7)}"
        value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        else -> "https://image.coolapk.com/${value.trimStart('/')}"
    }
}
