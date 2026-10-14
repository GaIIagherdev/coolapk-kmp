package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class SearchCategory(val apiValue: String, val label: String) {
    ALL("all", "综合"),
    APP("apk", "应用"),
    GAME("game", "游戏"),
    USER("user", "用户"),
    FEED("feed", "动态"),
    ASK("ask", "问答"),
    PRODUCT("product", "数码"),
    TOPIC("feedTopic", "话题"),
}

enum class SearchResultKind { APP, USER, FEED, QUESTION, PRODUCT, TOPIC, OTHER }

data class SearchResult(
    val id: String,
    val kind: SearchResultKind,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val targetId: String?,
)

data class SearchPage(
    val items: List<SearchResult>,
    val firstItem: String? = null,
    val lastItem: String? = null,
    val hasMore: Boolean = items.isNotEmpty(),
)

class SearchJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parsePage(raw: String, category: SearchCategory): SearchPage {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: return SearchPage(emptyList(), hasMore = false)
        val payload = root["data"] ?: return SearchPage(emptyList(), hasMore = false)
        val rows = payload.searchRows()
        val items = rows.mapNotNull { parseResult(it, category) }
            .distinctBy { "${it.kind}:${it.id}" }
        val fields = listOfNotNull(root, payload as? JsonObject)
        val firstItem = fields.firstNotNullOfOrNull { it.text("firstItem", "first_item") }
            ?: rows.firstOrNull()?.text("entityId", "entity_id", "id")
        val lastItem = fields.firstNotNullOfOrNull { it.text("lastItem", "last_item") }
            ?: rows.lastOrNull()?.text("entityId", "entity_id", "id")
        val noMore = fields.firstNotNullOfOrNull { it.bool("noMore", "no_more") }
        val hasMore = fields.firstNotNullOfOrNull { it.bool("hasMore", "has_more") }
        return SearchPage(items, firstItem, lastItem, hasMore ?: (noMore != true && rows.isNotEmpty()))
    }

    fun parseHotWords(raw: String): List<String> {
        val root = json.parseToJsonElement(raw)
        val payload = (root as? JsonObject)?.get("data") ?: root
        val words = mutableListOf<String>()
        fun visit(element: JsonElement?) {
            when (element) {
                is JsonArray -> element.forEach(::visit)
                is JsonObject -> {
                    if (element.isSponsor()) return
                    val children = listOf("entities", "entityList", "data", "list", "items", "children")
                        .mapNotNull { element[it] as? JsonArray }
                    if (children.isNotEmpty()) children.forEach { it.forEach(::visit) }
                    else element.text("title", "searchValue", "keyword", "word", "name")?.let(words::add)
                }
                is JsonPrimitive -> element.contentOrNull?.takeIf(String::isNotBlank)?.let(words::add)
                else -> Unit
            }
        }
        visit(payload)
        return words.distinct()
    }

    private fun JsonElement.searchRows(): List<JsonObject> = when (this) {
        is JsonArray -> flatMap { it.searchRows() }
        is JsonObject -> {
            if (isSponsor()) emptyList()
            else {
                val children = listOf("entities", "entityList", "list", "items", "rows", "result", "data")
                    .mapNotNull { this[it]?.takeIf { child -> child is JsonArray || child is JsonObject } }
                if (children.isNotEmpty()) children.flatMap { it.searchRows() }
                else if (text("title", "name", "username", "message", "searchValue") != null) listOf(this)
                else emptyList()
            }
        }
        else -> emptyList()
    }

    private fun parseResult(row: JsonObject, category: SearchCategory): SearchResult? {
        val type = row.text("entityType", "entity_type", "type")?.lowercase().orEmpty()
        val template = row.text("entityTemplate", "entity_template", "template")?.lowercase().orEmpty()
        val packageName = row.text("packageName", "package_name", "pkg", "apkname", "apkName")
        val uid = row.text("uid", "userId", "user_id")
            ?: (row["userInfo"] as? JsonObject)?.text("uid", "userId", "id")
            ?: (row["user"] as? JsonObject)?.text("uid", "userId", "id")
        val entityId = row.text("id", "entityId", "entity_id", "feedId", "feed_id")
        val kind = when {
            type.contains("user") || template.contains("user") -> SearchResultKind.USER
            type.contains("topic") || template.contains("topic") -> SearchResultKind.TOPIC
            type.contains("apk") || type.contains("app") || type.contains("game") || packageName != null -> SearchResultKind.APP
            type.contains("product") || template.contains("product") -> SearchResultKind.PRODUCT
            type.contains("question") || template.contains("question") -> SearchResultKind.QUESTION
            type.contains("feed") || type.contains("answer") || type.contains("article") ||
                type.contains("dyh") || row.text("message", "feedId", "feed_id") != null -> SearchResultKind.FEED
            category == SearchCategory.APP || category == SearchCategory.GAME -> SearchResultKind.APP
            category == SearchCategory.USER -> SearchResultKind.USER
            category == SearchCategory.PRODUCT -> SearchResultKind.PRODUCT
            category == SearchCategory.TOPIC -> SearchResultKind.TOPIC
            category == SearchCategory.FEED || category == SearchCategory.ASK -> SearchResultKind.FEED
            else -> SearchResultKind.OTHER
        }
        val title = row.text(
            "title", "message_title", "messageTitle", "questionTitle", "shorttitle", "appName",
            "name", "username", "entityTitle", "label", "searchValue", "message",
        ) ?: return null
        val targetId = when (kind) {
            SearchResultKind.APP -> packageName
            SearchResultKind.USER -> uid
            SearchResultKind.TOPIC -> row.text("tag", "topicTag", "topic_tag", "topicName")
                ?.trimStart('#')?.trim()?.takeIf(String::isNotBlank)
                ?: row.text("url", "actionUrl", "action_url", "link")?.topicTagFromUrl()
                ?: title.trimStart('#').trim()
            SearchResultKind.FEED, SearchResultKind.PRODUCT -> entityId
            SearchResultKind.QUESTION, SearchResultKind.OTHER -> null
        }?.takeIf(String::isNotBlank)
        val id = entityId ?: targetId ?: "$type:$title"
        return SearchResult(
            id = id,
            kind = kind,
            title = title,
            subtitle = row.text("subTitle", "subtitle", "description", "message", "summary", "bio", "sign")
                ?.takeUnless { it == title },
            imageUrl = row.text("apkRomIcon", "logo", "icon", "pic", "userAvatar", "cover", "image", "avatar")
                ?.normalizeSearchImageUrl(),
            targetId = targetId,
        )
    }

    private fun JsonObject.isSponsor(): Boolean =
        text("entityType", "entity_type", "type")?.startsWith("sponsor", true) == true ||
            text("entityTemplate", "entity_template", "template")?.startsWith("sponsor", true) == true ||
            text("sponsorType", "sponsor_type")?.let { it != "0" && it != "false" } == true

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
    }

    private fun JsonObject.bool(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
        when (text(key)?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    private fun String.normalizeSearchImageUrl(): String? = when {
        isBlank() -> null
        startsWith("https://") -> this
        startsWith("http://") -> "https://${removePrefix("http://")}"
        startsWith("//") -> "https:$this"
        else -> "https://image.coolapk.com/${trimStart('/')}"
    }

    private fun String.topicTagFromUrl(): String? {
        val encodedTag = Regex("/(?:topic|t)/([^/?#]+)", RegexOption.IGNORE_CASE)
            .find(this)?.groupValues?.getOrNull(1) ?: return null
        return encodedTag.percentDecoded().trimStart('#').trim().takeIf(String::isNotBlank)
    }

    private fun String.percentDecoded(): String {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < length) {
            if (this[index] == '%' && index + 2 < length) {
                val value = substring(index + 1, index + 3).toIntOrNull(16)
                if (value != null) {
                    bytes.add(value.toByte())
                    index += 3
                    continue
                }
            }
            val codePoint = substring(index, index + 1).encodeToByteArray()
            bytes.addAll(codePoint.toList())
            index++
        }
        return bytes.toByteArray().decodeToString()
    }
}
