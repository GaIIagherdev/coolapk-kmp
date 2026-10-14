package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 关注或粉丝列表中的一位用户。 */
data class UserSummary(
    val uid: String,
    val username: String,
    val avatarUrl: String?,
    val bio: String?,
    val isFollow: Boolean,
    val isFans: Boolean,
)

data class UserSummaryPage(
    val items: List<UserSummary>,
    val hasMore: Boolean,
)

class UserListJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parsePage(raw: String): UserSummaryPage {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return UserSummaryPage(emptyList(), false)
        val rows = (root["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val items = rows.mapNotNull { row ->
            val uid = row.text("uid", "userId", "id") ?: return@mapNotNull null
            UserSummary(
                uid = uid,
                username = row.text("username", "userName") ?: "酷友",
                avatarUrl = row.text("userAvatar", "avatar")?.let(::normalizeAvatar),
                bio = (row.text("bio", "signature", "sign"))?.takeIf { it.isNotBlank() },
                isFollow = row.text("isFollow")?.let { it == "1" || it == "true" } ?: false,
                isFans = row.text("isFans")?.let { it == "1" || it == "true" } ?: false,
            )
        }
        return UserSummaryPage(items = items, hasMore = rows.isNotEmpty())
    }

    /** 二维码接口可能返回字符串，或包含 image / imageUrl / url 的对象。 */
    fun parseQrImage(raw: String): String? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val data: JsonElement = root["data"] ?: return null
        return when (data) {
            is JsonPrimitive -> data.contentOrNull?.takeIf(String::isNotBlank)
            is JsonObject -> data.text("image", "imageUrl", "image_url", "url")
            else -> null
        }
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun normalizeAvatar(value: String): String = when {
        value.startsWith("http://") -> "https://" + value.removePrefix("http://")
        value.startsWith("//") -> "https:$value"
        else -> value
    }
}
