package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 主页卡片中的一个实体（用户、话题或普通卡片）。 */
data class HomeCardEntity(
    val id: String,
    val title: String?,
    val subTitle: String?,
    val imageUrl: String?,
    val url: String?,
    val uid: String?,
    val entityType: String?,
)

enum class HomeCardKind { USER, TOPIC, GENERIC }

/** 主页的一个分区：关注的人、关注的板块或其它卡片。 */
data class HomeCardRow(
    val title: String,
    val subTitle: String?,
    val kind: HomeCardKind,
    val entities: List<HomeCardEntity>,
)

class UserHomeJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parseRows(raw: String): List<HomeCardRow> {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
        val payload = root["data"] as? JsonObject ?: root
        val rows = payload["homeTabCardRows"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val entities = (row["entities"] as? JsonArray ?: row["list"] as? JsonArray ?: row["data"] as? JsonArray)
                .orEmpty()
                .mapNotNull { entity -> (entity as? JsonObject)?.toEntity() }
            if (entities.isEmpty()) return@mapNotNull null
            HomeCardRow(
                title = row.text("title", "name", "label").orEmpty(),
                subTitle = row.text("subTitle", "subtitle", "description"),
                kind = kindOf(row, entities),
                entities = entities,
            )
        }
    }

    private fun kindOf(row: JsonObject, entities: List<HomeCardEntity>): HomeCardKind {
        val type = (row.text("entityType", "entityTemplate") ?: "").lowercase()
        val title = row.text("title", "name", "label").orEmpty()
        return when {
            type.contains("user") || entities.all { it.uid != null } -> HomeCardKind.USER
            type.contains("topic") || type.contains("tag") || title.contains("板块") || title.contains("话题") ||
                entities.all { it.entityType?.contains("topic", true) == true } ->
                HomeCardKind.TOPIC
            else -> HomeCardKind.GENERIC
        }
    }

    private fun JsonObject.toEntity(): HomeCardEntity? {
        val id = text("id", "entityId", "uid", "tag") ?: return null
        return HomeCardEntity(
            id = id,
            title = text("username", "title", "userName", "name"),
            subTitle = text("signature", "bio", "subTitle", "description"),
            imageUrl = text("userAvatar", "avatar", "pic", "logo", "icon", "cover")?.normalizeHomeImage(),
            url = text("url", "targetUrl"),
            uid = text("uid", "userId"),
            entityType = text("entityType", "entityTemplate"),
        )
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun String.normalizeHomeImage(): String = when {
        startsWith("http://") -> "https://" + removePrefix("http://")
        startsWith("//") -> "https:$this"
        startsWith("http") -> this
        else -> "https://image.coolapk.com/${trimStart('/')}"
    }
}
