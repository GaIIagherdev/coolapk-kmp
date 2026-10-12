package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Rust 首阶段直接返回服务端 JSON，解析保持在共享层，避免 FFI 频繁增删字段。
 */
class FeedJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parseHomePage(raw: String): HomePage {
        val root = json.parseToJsonElement(raw).jsonObject
        val payload = root["data"] ?: return HomePage(emptyList(), hasMore = false)
        val list = payload.asFeedArray()
        val first = list.firstOrNull()?.string("entityId")
        val last = list.lastOrNull()?.string("entityId")
        return HomePage(
            items = list.mapNotNull(::parseFeed),
            firstItem = first,
            lastItem = last,
            hasMore = list.isNotEmpty(),
        )
    }

    private fun JsonElement.asFeedArray(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { element ->
            element as? JsonObject ?: element.takeIf { it is JsonArray }?.jsonArray?.firstOrNull() as? JsonObject
        }
        is JsonObject -> {
            sequenceOf("data", "entities", "feeds", "list")
                .mapNotNull { key -> this[key]?.takeIf { it is JsonArray }?.jsonArray }
                .firstOrNull()
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
        }
        else -> emptyList()
    }

    private fun parseFeed(obj: JsonObject): FeedItem? {
        val id = obj.string("entityId") ?: obj.string("id") ?: return null
        val imageUrls = obj["picArr"]
            ?.takeIf { it is JsonArray }
            ?.jsonArray
            ?.mapNotNull { it.primitiveOrNull()?.normalizeImageUrl() }
            .orEmpty()
        return FeedItem(
            id = id,
            title = obj.string("title"),
            message = obj.string("message") ?: obj.string("messageRaw") ?: "",
            username = obj.string("username") ?: obj.string("userName") ?: "酷安用户",
            avatarUrl = obj.string("userAvatar")?.normalizeImageUrl(),
            device = obj.string("deviceTitle") ?: obj.string("deviceName"),
            createdAt = obj.string("dateline")?.takeIf { it.isNotBlank() },
            likeCount = obj.int("likenum") ?: obj.int("likeCount") ?: 0,
            replyCount = obj.int("replynum") ?: obj.int("replyCount") ?: 0,
            imageUrls = imageUrls,
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.primitiveOrNull()?.takeIf(String::isNotBlank)

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonElement.primitiveOrNull(): String? = runCatching {
        jsonPrimitive.contentOrNull
    }.getOrNull()

    private fun String.normalizeImageUrl(): String {
        val value = trim()
        return if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "https://image.coolapk.com/${value.trimStart('/')}"
        }
    }
}
