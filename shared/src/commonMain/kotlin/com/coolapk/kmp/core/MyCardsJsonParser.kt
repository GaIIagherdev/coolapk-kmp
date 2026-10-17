package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 「我的常去」中的一张卡片，来自个人中心“我的卡片”配置。 */
data class MyCard(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val url: String?,
)

/**
 * 解析个人中心“我的卡片”配置，只取「常去」分组下的卡片。
 * 逻辑移植自参考项目 utils/myCards.ts；字段依据参考项目推断，未经真实样本验证。
 */
class MyCardsJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parse(raw: String): List<MyCard> {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val groups = mutableListOf<JsonObject>()
        walk(root) { record ->
            if (isFrequentGroup(record) && children(record).isNotEmpty()) groups += record
        }
        val groupRecords = mutableListOf<JsonObject>()
        groups.forEach { group -> children(group).forEach { collect(it, groupRecords) } }
        if (groupRecords.isNotEmpty()) return unique(groupRecords)

        // 兼容接口直接返回卡片数组的旧响应：分组标题被去掉时仍能显示卡片。
        val fallback = mutableListOf<JsonObject>()
        collect(root, fallback)
        return unique(fallback)
    }

    private fun isFrequentGroup(record: JsonObject): Boolean {
        val text = listOf(
            text(record, "title", "name", "label").orEmpty(),
            text(record, "subTitle", "subtitle", "description").orEmpty(),
        ).joinToString(" ")
        return text.contains("常去")
    }

    private fun cardFrom(record: JsonObject, index: Int): MyCard? {
        val title = text(record, *TITLE_KEYS).orEmpty()
        val url = text(record, *URL_KEYS)
        val image = text(record, *IMAGE_KEYS)
        if (title.isEmpty() && url == null && image == null) return null
        val id = text(record, *ID_KEYS) ?: url ?: "$title-$index"
        return MyCard(
            id = id,
            title = title.ifEmpty { "未命名卡片" },
            imageUrl = image?.let(::normalizeImage),
            url = url,
        )
    }

    private fun collect(element: JsonElement, result: MutableList<JsonObject>) {
        val parsed = parseEmbeddedJson(element)
        when (parsed) {
            is JsonArray -> parsed.forEach { collect(it, result) }
            is JsonObject -> {
                val childList = children(parsed)
                val hasUrlOrImage = text(parsed, *URL_KEYS) != null || text(parsed, *IMAGE_KEYS) != null
                if (childList.isNotEmpty() && !hasUrlOrImage) {
                    childList.forEach { collect(it, result) }
                    return
                }
                val hasCardFields = text(parsed, *TITLE_KEYS) != null || hasUrlOrImage
                if (hasCardFields) result += parsed
            }
            else -> Unit
        }
    }

    private fun unique(records: List<JsonObject>): List<MyCard> {
        val seen = mutableSetOf<String>()
        return records.mapIndexedNotNull { index, record ->
            val card = cardFrom(record, index) ?: return@mapIndexedNotNull null
            val key = "${card.id}|${card.url}|${card.title}"
            if (seen.add(key)) card else null
        }
    }

    private fun walk(element: JsonElement, visitor: (JsonObject) -> Unit) {
        when (val parsed = parseEmbeddedJson(element)) {
            is JsonArray -> parsed.forEach { walk(it, visitor) }
            is JsonObject -> {
                visitor(parsed)
                parsed.values.forEach { walk(it, visitor) }
            }
            else -> Unit
        }
    }

    /** 参考项目里部分字段是 JSON 字符串，需要再解析一层。 */
    private fun parseEmbeddedJson(element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive ?: return element
        if (primitive.isString.not()) return element
        val trimmed = primitive.contentOrNull?.trim().orEmpty()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return element
        return runCatching { json.parseToJsonElement(trimmed) }.getOrDefault(element)
    }

    private fun children(record: JsonObject): List<JsonObject> = CHILD_KEYS.flatMap { key ->
        when (val value = parseEmbeddedJson(record[key] ?: return@flatMap emptyList())) {
            is JsonArray -> value.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(value)
            else -> emptyList()
        }
    }

    /** 取第一个非空字符串；嵌套对象时依次尝试 url、src、href、value。 */
    private fun text(record: JsonObject, vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        when (val value = record[key]) {
            is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            is JsonObject -> listOf("url", "src", "href", "value")
                .firstNotNullOfOrNull { nested -> (value[nested] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            else -> null
        }
    }

    private fun normalizeImage(value: String): String = when {
        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
        value.startsWith("http") -> value
        value.startsWith("//") -> "https:$value"
        else -> "https://image.coolapk.com/${value.trimStart('/')}"
    }

    private companion object {
        val CHILD_KEYS = arrayOf("entities", "items", "list", "cards", "children", "rows", "data")
        val TITLE_KEYS = arrayOf("title", "name", "label", "username", "userName", "nodeName", "topicName")
        val URL_KEYS = arrayOf("url", "actionUrl", "action_url", "targetUrl", "target_url", "webUrl", "web_url", "link", "href", "jumpUrl", "jump_url")
        val IMAGE_KEYS = arrayOf("pic", "logo", "icon", "image", "cover", "avatar", "userAvatar", "user_avatar", "thumb", "thumbnail")
        val ID_KEYS = arrayOf("entityId", "entity_id", "id", "uid", "userId", "user_id")
    }
}
