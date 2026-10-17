package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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
            uid = obj.string("uid") ?: obj.string("userId") ?: obj.string("user_id")
                ?: user?.string("uid") ?: user?.string("userId") ?: user?.string("id"),
            questionId = questionIdOf(obj, id),
            collectionItemId = collectionItemIdOf(obj),
        )
    }

    /**
     * 收藏单内容列表把条目 ID 放在 collectionItem / collection_item_info 等嵌套对象里，
     * 与动态 ID 不同；移出收藏单必须使用这个 ID。
     */
    private fun collectionItemIdOf(obj: JsonObject): String? {
        val nested = sequenceOf("collectionItem", "collection_item_info", "collectionItemInfo")
            .mapNotNull { key -> obj[key] as? JsonObject }
            .firstOrNull()
        return nested?.string("id")
            ?: nested?.string("itemId")
            ?: nested?.string("item_id")
            ?: obj.string("collectionItemId")
            ?: obj.string("collection_item_id")
    }

    /**
     * 与参考项目 isQuestionFeedEntity 一致：实体类型标记含 question（回答除外）即为问题动态。
     * 问题动态的问题 ID 取 questionId / question_id，缺失时用动态 ID。
     */
    private fun questionIdOf(obj: JsonObject, id: String): String? {
        val markers = listOf(
            "entityType", "entity_type", "feedType", "feed_type", "feedTypeName", "feed_type_name", "type",
            "questionType", "question_type", "entityTemplate", "entity_template", "template",
        ).mapNotNull { obj.string(it)?.lowercase() }
        if (markers.any { it.contains("answer") || it.contains("回答") }) return null
        val isQuestion = markers.any {
            it == "question" || it == "feed_question" || it == "feedquestion" || it.contains("question") || it.contains("提问")
        } || obj["question"] is JsonObject
        if (!isQuestion) return null
        return obj.string("questionId") ?: obj.string("question_id") ?: id
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
            "feed", "feedreply", "article", "news", "dyh", "question", "answer", "feedquestion", "feed_question",
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
    /** 当前公历年份，用于计算生日年龄；返回 0 时不显示年龄。 */
    private val currentYear: () -> Int = { 0 },
) {
    fun parse(raw: String): UserProfile? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val obj = root["data"] as? JsonObject ?: return null
        val uid = obj.string("uid") ?: obj.string("id") ?: return null
        val info = obj["userInfo"] as? JsonObject ?: JsonObject(emptyMap())
        fun field(vararg keys: String): JsonElement? = keys.firstNotNullOfOrNull { key ->
            obj[key]?.takeIf { it !is JsonNull } ?: info[key]?.takeIf { it !is JsonNull }
        }
        fun text(vararg keys: String): String? = field(*keys)?.primitiveOrNull()?.trim()?.takeIf(String::isNotEmpty)
        fun number(vararg keys: String): Long? = text(*keys)?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }
        val gender = text("gender")?.toIntOrNull()
        val birthYear = number("birthyear", "birthYear", "birth_year")?.toInt()
        val birthMonth = number("birthmonth", "birthMonth", "birth_month")?.toInt()
        val birthDay = number("birthday", "birthDay", "birth_day")?.toInt()
        val year = currentYear()
        val ageTag = text("age_group", "ageGroup", "age_tag", "ageTag", "age_group_tag")
            ?: birthYear?.let { year -> "${((year - year % 5) % 100).toString().padStart(2, '0')}后" }
        val astro = text("astro", "constellation", "zodiacSign", "zodiac_sign")
            ?: if (birthMonth != null && birthDay != null) zodiacOf(birthMonth, birthDay) else null
        val birthLabel = when {
            birthYear != null && birthMonth != null && birthDay != null ->
                "${birthYear}年${birthMonth}月${birthDay}日" +
                    if (year > birthYear) " (${year - birthYear}岁)" else ""
            birthYear != null && birthMonth != null -> "${birthYear}年${birthMonth}月"
            birthMonth != null && birthDay != null -> "${birthMonth}月${birthDay}日"
            else -> null
        }
        return UserProfile(
            uid = uid,
            username = obj.string("username") ?: obj.string("userName")
                ?: obj.string("displayUserName") ?: obj.string("displayUsername")
                ?: obj.string("user_name") ?: "酷安用户",
            avatarUrl = (obj.string("userAvatar") ?: obj.string("avatar")
                ?: obj.string("user_avatar"))?.normalizeImageUrl(),
            bio = obj.string("bio") ?: obj.string("signature") ?: obj.string("sign")
                ?: obj.string("description"),
            feedCount = obj.int("feed") ?: obj.int("feedNum") ?: obj.int("feednum")
                ?: obj.int("feedCount") ?: obj.int("feed_num") ?: obj.int("feed_count"),
            followingCount = obj.int("followNum") ?: obj.int("follownum") ?: obj.int("follow")
                ?: obj.int("followCount") ?: obj.int("follow_num") ?: 0,
            followerCount = obj.int("fansNum") ?: obj.int("fansnum") ?: obj.int("fans")
                ?: obj.int("fansCount") ?: obj.int("fans_num") ?: 0,
            cover = text("cover")?.normalizeImageUrl(),
            level = number("level")?.toInt(),
            exp = number("exp")?.toInt(),
            maxExp = number("maxExp", "max_exp")?.toInt(),
            likeCount = number("be_like_num", "beLikeNum", "likeNum")?.toInt(),
            gender = gender,
            ageTag = ageTag,
            astro = astro,
            birthLabel = birthLabel,
            city = text("city", "location")?.takeUnless { it in UNKNOWN_PLACES },
            province = text("province")?.takeUnless { it in UNKNOWN_PLACES },
            birthYear = birthYear,
            birthMonth = birthMonth,
            birthDay = birthDay,
            ipLocation = text("ip_location", "ipLocation", "ip_region", "ipRegion")
                ?.takeUnless { it in UNKNOWN_PLACES },
            loginTime = number("logintime", "loginTime"),
            regDate = number("regdate", "regDate"),
            verifyTitle = text("verify_title", "verifyTitle", "verify_show_name"),
            isDeveloper = field("isDeveloper")?.primitiveOrNull() in setOf("1", "true"),
            isFollow = field("isFollow")?.primitiveOrNull() in setOf("1", "true"),
            isSpecialFollow = field("isSpecialFollow")?.primitiveOrNull() in setOf("1", "true"),
            isFans = field("isFans")?.primitiveOrNull() in setOf("1", "true"),
            productOwnerCount = number("product_owner_count", "productOwnerCount")?.toInt(),
            goodsCount = number("goods_count", "goodsCount")?.toInt(),
        )
    }

    private fun zodiacOf(month: Int, day: Int): String? {
        if (month !in 1..12 || day !in 1..31) return null
        val signs = listOf(
            Triple("摩羯座", 1, 19), Triple("水瓶座", 2, 18), Triple("双鱼座", 3, 20), Triple("白羊座", 4, 19),
            Triple("金牛座", 5, 20), Triple("双子座", 6, 21), Triple("巨蟹座", 7, 22), Triple("狮子座", 8, 22),
            Triple("处女座", 9, 22), Triple("天秤座", 10, 23), Triple("天蝎座", 11, 22), Triple("射手座", 12, 21),
        )
        val match = signs.firstOrNull { (_, endMonth, endDay) -> month == endMonth && day <= endDay }
        return (match ?: if (month == 12) signs[0] else signs[month - 1]).first
    }

    private companion object {
        val UNKNOWN_PLACES = setOf("保密", "未知", "未公开", "")
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
