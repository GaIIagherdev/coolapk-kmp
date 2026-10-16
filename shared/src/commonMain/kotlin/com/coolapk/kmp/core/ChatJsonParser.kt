package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 私信会话列表中的一项。 */
data class ChatSession(
    val ukey: String,
    val uid: String?,
    val username: String,
    val avatarUrl: String?,
    val lastMessage: String,
    val time: String?,
    val unread: Int,
)

/** 聊天记录中的一条消息。 */
data class ChatMessage(
    val id: String,
    val uid: String?,
    val username: String,
    val avatarUrl: String?,
    val text: String,
    val time: String?,
)

data class ChatMessagePage(
    val items: List<ChatMessage>,
    val firstItem: String?,
    val lastItem: String?,
    val hasMore: Boolean,
)

class ChatJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parseSessions(raw: String): List<ChatSession> {
        val rows = rows(raw)
        return rows.mapNotNull { row ->
            val ukey = row.text("ukey", "uKey", "id") ?: return@mapNotNull null
            ChatSession(
                ukey = ukey,
                uid = row.text("uid", "userId"),
                username = row.text("username", "userName") ?: "酷友",
                avatarUrl = row.text("userAvatar", "avatar")?.let(::normalizeAvatar),
                lastMessage = row.text("message", "lastMessage", "content").orEmpty().plainText(),
                time = row.text("dateline", "time", "lastTime"),
                unread = row.text("unreadCount", "unread", "unread_count")?.toIntOrNull() ?: 0,
            )
        }
    }

    fun parseHistory(raw: String): ChatMessagePage {
        val rows = rows(raw)
        val items = rows.mapNotNull { row ->
            val id = row.text("id", "messageId", "message_id") ?: return@mapNotNull null
            ChatMessage(
                id = id,
                uid = row.text("uid", "userId"),
                username = row.text("username", "userName") ?: "酷友",
                avatarUrl = row.text("userAvatar", "avatar")?.let(::normalizeAvatar),
                text = row.text("message", "content").orEmpty().plainText(),
                time = row.text("dateline", "time"),
            )
        }
        return ChatMessagePage(
            items = items,
            firstItem = items.firstOrNull()?.id,
            lastItem = items.lastOrNull()?.id,
            hasMore = rows.isNotEmpty(),
        )
    }

    private fun rows(raw: String): List<JsonObject> {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
        return (root["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun String.plainText(): String = replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()

    private fun normalizeAvatar(value: String): String = when {
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        value.startsWith("//") -> "https:$value"
        else -> value
    }
}
