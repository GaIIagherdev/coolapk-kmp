package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class NotificationCategory(val apiValue: String, val title: String) {
    COMMENT("list", "评论回复"),
    MENTION("atMeList", "@ 提及"),
    COMMENT_MENTION("atCommentMeList", "评论 @"),
    LIKE("feedLikeList", "收到的赞"),
    FOLLOW("contactsFollowList", "新关注"),
}

data class NotificationItem(
    val id: String,
    val actorName: String,
    val actorAvatarUrl: String?,
    val message: String,
    val targetPreview: String?,
    val createdAt: String?,
    val feedId: String?,
    val unreadCount: Int = 0,
)

data class NotificationPage(
    val items: List<NotificationItem>,
    val hasMore: Boolean = items.isNotEmpty(),
)

class NotificationListJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parsePage(raw: String, category: NotificationCategory): NotificationPage {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return NotificationPage(emptyList())
        val payload = root["data"] ?: return NotificationPage(emptyList())
        val rows = payload.rows()
        val items = rows.mapIndexed { index, row -> parseItem(row, category, index) }
        val pageInfo = root["pagination"] as? JsonObject
            ?: root["pageInfo"] as? JsonObject
            ?: (payload as? JsonObject)?.get("pagination") as? JsonObject
        val more = root.value("hasMore", "has_more") ?: pageInfo?.value("hasMore", "has_more")
        return NotificationPage(
            items = items,
            hasMore = when (more?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> rows.isNotEmpty()
            },
        )
    }

    private fun parseItem(row: JsonObject, category: NotificationCategory, index: Int): NotificationItem {
        val actor = when (category) {
            NotificationCategory.LIKE -> row["likeUserInfo"] as? JsonObject
            else -> row["fromUserInfo"] as? JsonObject ?: row["userInfo"] as? JsonObject
        }
        val actorName = when (category) {
            NotificationCategory.LIKE -> row.value("likeUsername") ?: actor?.value("username")
            else -> row.value("fromusername", "username") ?: actor?.value("username", "name")
        } ?: "酷友"
        val avatar = when (category) {
            NotificationCategory.LIKE -> row.value("likeAvatar") ?: actor?.value("userAvatar", "avatar")
            else -> row.value("fromUserAvatar", "userAvatar")
                ?: actor?.value("userAvatar", "avatar")
        }?.imageUrl()
        val feedInfo = row["feedInfo"] as? JsonObject
        val targetFeed = row["targetFeed"] as? JsonObject
        val target = row["targetRow"] as? JsonObject
        val firstReply = (row["replyRows"] as? JsonArray)?.firstOrNull() as? JsonObject
        val message = row.value("note", "message", "message_title")
            ?: firstReply?.value("message")
            ?: when (category) {
                NotificationCategory.LIKE -> "赞了你的${row.value("feedTypeName") ?: "动态"}"
                NotificationCategory.FOLLOW -> "关注了你"
                NotificationCategory.MENTION, NotificationCategory.COMMENT_MENTION ->
                    feedInfo?.value("message") ?: target?.value("message") ?: "提到了你"
                NotificationCategory.COMMENT ->
                    feedInfo?.value("message") ?: target?.value("message") ?: "回复了你"
            }
        val id = row.value("id", "entityId", "entity_id")
            ?: listOf(category.apiValue, actorName, row.value("dateline", "likeTime").orEmpty(), index)
                .joinToString(":")
        val preview = row.value("targetTitle")
            ?: feedInfo?.value("message_title", "title", "message")
            ?: target?.value("message_title", "title", "message")
        val feedId = sequenceOf(row, feedInfo, targetFeed, target)
            .filterNotNull()
            .mapNotNull { source ->
                source.value("note", "message", "infoHtml", "url", "targetUrl")
                    ?.let { FEED_LINK.find(it)?.groupValues?.getOrNull(1) }
                    ?: source.value("feedId", "feed_id", "fid")?.takeIf { it.all(Char::isDigit) }
                    ?: source.value("id", "entityId", "targetId")
                        ?.takeIf {
                            source !== row && (source === feedInfo || source === targetFeed || source.isFeed()) &&
                                it.all(Char::isDigit)
                        }
            }.firstOrNull()
        val unread = row.value("unread_count", "unreadCount")?.toIntOrNull()
            ?: if (row.value("isnew", "isNew") in setOf("1", "true")) 1 else 0
        return NotificationItem(
            id = id,
            actorName = actorName,
            actorAvatarUrl = avatar,
            message = message,
            targetPreview = preview,
            createdAt = row.value("dateline", "likeTime", "lastUpdate"),
            feedId = feedId,
            unreadCount = unread.coerceAtLeast(0),
        )
    }

    private fun JsonObject.isFeed(): Boolean {
        val type = value("entityType", "entity_type", "targetType", "feedType").orEmpty()
        return type.contains("feed", true) || value("feedId", "feed_id", "fid") != null ||
            value("message", "message_title") != null
    }

    private fun JsonElement.rows(): List<JsonObject> = when (this) {
        is JsonArray -> flatMap { it.rows() }
        is JsonObject -> {
            val nested = this["data"] ?: this["list"] ?: this["entities"] ?: this["rows"]
            if (nested != null && value("id", "entityId", "entity_id", "note", "message") == null) {
                nested.rows()
            } else {
                listOf(this)
            }
        }
        else -> emptyList()
    }

    private fun JsonObject.value(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        }

    private fun String.imageUrl(): String? {
        val value = trim()
        return when {
            value.isEmpty() || value in setOf("null", "undefined") -> null
            value.startsWith("https://", true) -> value
            value.startsWith("http://", true) -> "https://${value.substring(7)}"
            value.startsWith("//") -> "https:$value"
            else -> "https://image.coolapk.com/${value.trimStart('/')}"
        }
    }

    private companion object {
        val FEED_LINK = Regex("(?:^|[/?#])feed/(\\d+)", RegexOption.IGNORE_CASE)
    }
}
