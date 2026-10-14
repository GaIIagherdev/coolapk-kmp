package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class TopicDetail(
    val tag: String,
    val title: String,
    val logoUrl: String?,
    val description: String?,
    val viewCount: String?,
    val discussionCount: String?,
    val followerCount: String?,
)

enum class TopicFeedSort(val label: String, val apiValue: String) {
    DEFAULT("默认", ""),
    LATEST("最新", "dateline_desc"),
    HOT("热度", "popular"),
}

class TopicJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parseDetail(raw: String, tag: String): TopicDetail {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: error("话题详情响应格式无效")
        val data = root["data"] as? JsonObject
            ?: error("话题详情缺少数据")
        val title = data.text("title", "tag", "name", "topicName") ?: tag
        return TopicDetail(
            tag = tag,
            title = title,
            logoUrl = data.text("logo", "pic", "cover", "icon")?.normalizeTopicImageUrl(),
            description = data.text("description", "intro"),
            viewCount = data.text("view_num", "hot_num", "click"),
            discussionCount = data.text("commentnum", "discuss_num", "replynum"),
            followerCount = data.text("follower_num", "follownum", "follow_num"),
        )
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
    }

    private fun String.normalizeTopicImageUrl(): String = when {
        startsWith("https://") -> this
        startsWith("http://") -> "https://${removePrefix("http://")}" 
        startsWith("//") -> "https:$this"
        else -> "https://image.coolapk.com/${trimStart('/')}"
    }
}
