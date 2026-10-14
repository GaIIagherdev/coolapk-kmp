package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Rust 首阶段直接返回服务端 JSON，解析保持在共享层，避免 FFI 频繁增删字段。
 */
class FeedJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parseHomePage(raw: String): HomePage {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: return HomePage(emptyList(), hasMore = false)
        return parsePage(root)
    }

    fun parsePage(root: JsonObject): HomePage {
        val payload = root["data"] ?: return HomePage(emptyList(), hasMore = false)
        val items = payload.asFeedObjects().mapNotNull(::parseFeed)
        val cursorRows = payload.asCursorRows()
        return HomePage(
            items = items,
            firstItem = root.pageString(payload, "firstItem", "first_item")
                ?: cursorRows.firstOrNull()?.cursorId(),
            lastItem = root.pageString(payload, "lastItem", "last_item")
                ?: cursorRows.lastOrNull()?.cursorId(),
            hasMore = root.pageBoolean(payload, "hasMore", "has_more")
                ?: cursorRows.isNotEmpty(),
        )
    }

    /**
     * indexV8 既能直接返回实体数组，也可能把动态放在 entities/feeds/list 等容器中；
     * 某些卡片还会出现一层包装数组。这里统一递归展开，同时避免把正文内引用的 feed 误当列表。
     */
    private fun JsonElement.asFeedObjects(): List<JsonObject> = when (this) {
        is JsonArray -> flatMap { element -> element.asFeedObjects() }
        is JsonObject -> {
            val direct = takeIf { it.isFeedEntity() }
            if (direct != null) {
                listOf(direct)
            } else {
                sequenceOf("data", "entities", "feeds", "list")
                    .mapNotNull { key -> this[key]?.asFeedObjects() }
                    .flatten()
                    .toList()
            }
        }
        else -> emptyList()
    }

    private fun JsonElement.asCursorRows(): List<JsonObject> = when (this) {
        is JsonArray -> flatMap { it.asCursorRows() }
        is JsonObject -> if (cursorId() != null) {
            listOf(this)
        } else {
            sequenceOf("data", "entities", "feeds", "list")
                .mapNotNull { this[it]?.asCursorRows() }
                .flatten()
                .toList()
        }
        else -> emptyList()
    }

    private fun JsonObject.cursorId(): String? = string("entityId") ?: string("entity_id")
        ?: string("id")

    private fun JsonObject.isFeedEntity(): Boolean {
        val id = string("entityId") ?: string("id") ?: return false
        val entityType = (string("entityType") ?: string("entity_type"))?.lowercase()
        return if (entityType != null) {
            entityType in FEED_ENTITY_TYPES || entityType.startsWith("feed_")
        } else {
            string("message") != null || string("messageRaw") != null ||
                string("title") != null && string("username") != null
        }
    }

    private fun parseFeed(obj: JsonObject): FeedItem? {
        val id = obj.string("id") ?: obj.string("entityId") ?: return null
        val user = (obj["userInfo"] ?: obj["user"]) as? JsonObject
        val imageUrls = sequenceOf("pics", "imageUriList", "picArr")
            .mapNotNull { key ->
                (obj[key] as? JsonArray)?.mapNotNull { image -> image.imageUrl() }
                    ?.takeIf(List<String>::isNotEmpty)
            }
            .firstOrNull()
            ?: listOfNotNull(obj.string("pic")?.normalizeImageUrl())
        return FeedItem(
            id = id,
            title = obj.string("title"),
            message = obj.string("message") ?: obj.string("messageRaw") ?: "",
            username = obj.string("username") ?: obj.string("userName")
                ?: user?.string("username") ?: user?.string("name") ?: "酷安用户",
            avatarUrl = (obj.string("userAvatar") ?: user?.string("userAvatar"))?.normalizeImageUrl(),
            device = obj.string("deviceTitle") ?: obj.string("deviceName"),
            createdAt = obj.string("dateline")?.takeIf { it.isNotBlank() },
            likeCount = obj.int("likenum") ?: obj.int("likeCount") ?: 0,
            replyCount = obj.int("replynum") ?: obj.int("replyCount") ?: 0,
            imageUrls = imageUrls,
            isLiked = (obj["userAction"] as? JsonObject)?.boolean("like")
                ?: obj.boolean("isLiked") ?: obj.boolean("isLike")
                ?: obj.boolean("is_liked") ?: false,
        )
    }

    private fun JsonElement.imageUrl(): String? {
        val value = when (this) {
            is JsonObject -> sequenceOf(
                "sourceUrl", "source_url", "inSource", "in_source", "url", "pic",
                "imageUrl", "image_url", "src",
            ).mapNotNull { key -> string(key) }.firstOrNull()
            else -> primitiveOrNull()
        }
        return value?.normalizeImageUrl()
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.primitiveOrNull()?.takeIf(String::isNotBlank)

    private fun JsonObject.int(key: String): Int? = this[key]?.primitiveOrNull()?.toIntOrNull()

    private fun JsonObject.boolean(key: String): Boolean? = when (this[key]?.primitiveOrNull()?.lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }

    private companion object {
        val FEED_ENTITY_TYPES = setOf(
            "feed", "feedreply", "article", "news", "dyh", "question", "answer",
        )
    }
}

class AppJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parsePage(raw: String): AppPage {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: return AppPage(emptyList(), hasMore = false)
        val payload = root["data"] ?: return AppPage(emptyList(), hasMore = false)
        val objects = payload.asEntityObjects()
        val items = objects.mapNotNull(::parseApp).distinctBy { it.id }
        return AppPage(
            items = items,
            hasMore = root.pageBoolean(payload, "hasMore", "has_more") ?: objects.isNotEmpty(),
        )
    }

    private fun JsonElement.asEntityObjects(): List<JsonObject> = when (this) {
        is JsonArray -> flatMap { it.asEntityObjects() }
        is JsonObject -> {
            if (looksLikeApp()) {
                listOf(this)
            } else {
                sequenceOf("data", "entities", "list", "rows", "result")
                    .mapNotNull { this[it]?.asEntityObjects() }
                    .flatten()
                    .toList()
            }
        }
        else -> emptyList()
    }

    private fun JsonObject.looksLikeApp(): Boolean =
        string("packageName") != null || string("apkPackageName") != null ||
            string("package_name") != null || string("apkName") != null ||
            string("apkname") != null ||
            string("entityType") in setOf("apk", "game") && string("title") != null

    private fun parseApp(obj: JsonObject): AppItem? {
        val packageName = obj.string("packageName")
            ?: obj.string("apkPackageName")
            ?: obj.string("package_name")
            ?: obj.string("apkname")
        val id = obj.string("id") ?: obj.string("entityId") ?: packageName ?: return null
        val name = obj.string("appName") ?: obj.string("apkName") ?: obj.string("title")
            ?: obj.string("name") ?: obj.string("shorttitle") ?: return null
        return AppItem(
            id = id,
            name = name,
            packageName = packageName,
            iconUrl = (obj.string("icon") ?: obj.string("logo") ?: obj.string("apkRomIcon")
                ?: obj.string("apkLogo"))
                ?.normalizeImageUrl(),
            summary = obj.string("description") ?: obj.string("brief") ?: obj.string("subTitle"),
            version = obj.string("versionName") ?: obj.string("version"),
            score = obj.double("score") ?: obj.double("rating"),
            downloadCount = obj.string("downCountFormatted") ?: obj.string("downloadCount")
                ?: obj.string("downnum")
                ?: obj.string("download_num"),
        )
    }

    private fun JsonObject.string(key: String): String? = this[key]?.primitiveOrNull()
        ?.takeIf(String::isNotBlank)

    private fun JsonObject.double(key: String): Double? = this[key]?.primitiveOrNull()?.toDoubleOrNull()
}

class ProfileJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parse(raw: String): UserProfile? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val obj = root["data"] as? JsonObject ?: return null
        val uid = obj.string("uid") ?: obj.string("id") ?: return null
        return UserProfile(
            uid = uid,
            username = obj.string("username") ?: obj.string("userName")
                ?: obj.string("displayUserName") ?: obj.string("displayUsername")
                ?: obj.string("user_name") ?: "酷安用户",
            avatarUrl = (obj.string("userAvatar") ?: obj.string("avatar")
                ?: obj.string("user_avatar"))?.normalizeImageUrl(),
            bio = obj.string("bio") ?: obj.string("signature") ?: obj.string("sign")
                ?: obj.string("description"),
            feedCount = obj.int("feedNum") ?: obj.int("feednum") ?: obj.int("feedCount")
                ?: obj.int("feed_num") ?: 0,
            followingCount = obj.int("followNum") ?: obj.int("follownum") ?: obj.int("follow")
                ?: obj.int("followCount") ?: obj.int("follow_num") ?: 0,
            followerCount = obj.int("fansNum") ?: obj.int("fansnum") ?: obj.int("fans")
                ?: obj.int("fansCount") ?: obj.int("fans_num") ?: 0,
        )
    }

    private fun JsonObject.string(key: String): String? = this[key]?.primitiveOrNull()
        ?.takeIf(String::isNotBlank)

    private fun JsonObject.int(key: String): Int? = this[key]?.primitiveOrNull()?.toIntOrNull()
}

class NotificationJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parse(raw: String): NotificationSummary {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: return NotificationSummary(0)
        val payload = root["data"] ?: root
        val total = payload.primitiveOrNull()?.toIntOrNull()
            ?: (payload as? JsonObject)?.notificationTotal()
            ?: root.notificationTotal()
            ?: 0
        return NotificationSummary(total = total.coerceAtLeast(0))
    }

    private fun JsonObject.notificationTotal(): Int? {
        val totalKeys = listOf(
            "badge_v18", "badge", "count", "fcount", "total", "totalCount",
            "notificationCount", "unreadCount",
        )
        totalKeys.firstNotNullOfOrNull { key -> this[key]?.primitiveOrNull()?.toIntOrNull() }
            ?.let { return it }

        val categoryKeys = listOf(
            listOf("commentme", "commentMe", "comment"),
            listOf("atme", "atMe"),
            listOf("atcommentme", "atCommentMe"),
            listOf("feedlike", "feedLike"),
            listOf("contacts_follow", "contactsFollow", "follow"),
            listOf("message", "messageCount"),
        )
        val categoryCounts = categoryKeys.map { aliases ->
            aliases.firstNotNullOfOrNull { key -> this[key]?.primitiveOrNull()?.toIntOrNull() }
        }
        return categoryCounts.takeIf { counts -> counts.any { it != null } }
            ?.sumOf { it?.coerceAtLeast(0) ?: 0 }
    }
}

private fun JsonElement.primitiveOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun String.normalizeImageUrl(): String? {
    val value = trim()
    if (value.isEmpty() || value == "null" || value == "undefined") return null
    return when {
        value.startsWith("http://", ignoreCase = true) ->
            "https://" + value.substring("http://".length)
        value.startsWith("https://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        else -> "https://image.coolapk.com/${value.trimStart('/')}"
    }
}

private fun JsonObject.pageFields(payload: JsonElement): Sequence<JsonObject> {
    val data = payload as? JsonObject
    return sequenceOf(
        this,
        this["pagination"] as? JsonObject,
        this["pageInfo"] as? JsonObject,
        this["page_info"] as? JsonObject,
        data,
        data?.get("pagination") as? JsonObject,
        data?.get("pageInfo") as? JsonObject,
        data?.get("page_info") as? JsonObject,
    ).filterNotNull()
}

private fun JsonObject.pageString(payload: JsonElement, vararg keys: String): String? =
    pageFields(payload).mapNotNull { fields ->
        keys.firstNotNullOfOrNull { key ->
            fields[key]?.primitiveOrNull()?.takeIf(String::isNotBlank)
        }
    }.firstOrNull()

private fun JsonObject.pageBoolean(payload: JsonElement, vararg keys: String): Boolean? =
    pageFields(payload).mapNotNull { fields ->
        keys.firstNotNullOfOrNull { key ->
            when (fields[key]?.primitiveOrNull()?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
        }
    }.firstOrNull()
