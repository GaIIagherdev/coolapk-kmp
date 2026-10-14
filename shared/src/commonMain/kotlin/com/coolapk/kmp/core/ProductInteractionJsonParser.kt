package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 产品点评：一位用户对产品的星级评分与文字点评。 */
data class ProductRating(
    val id: String,
    val uid: String?,
    val username: String,
    val avatarUrl: String?,
    val message: String,
    val score: Int?,
    val time: String?,
    val device: String?,
    val pics: List<String>,
)

data class ProductRatingPage(
    val items: List<ProductRating>,
    val hasMore: Boolean,
)

/** 评分趋势图上的一个数据点。 */
data class RatingChartPoint(
    val label: String,
    val score: Double,
    val count: Int,
)

/** 评分趋势，按周期（day / week / month）分组，每组内按时间排序。 */
data class ProductRatingChart(
    val periods: Map<String, List<RatingChartPoint>>,
) {
    fun series(period: String): List<RatingChartPoint> = periods[period].orEmpty()
}

class ProductInteractionJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parseRatings(raw: String): ProductRatingPage {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return ProductRatingPage(emptyList(), false)
        val rows = (root["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val items = rows.mapIndexedNotNull { index, row ->
            val message = row.text("message", "content", "rating_message")?.let(::plainText).orEmpty()
            val username = row.text("username", "user_name")
            if (message.isBlank() && username == null) return@mapIndexedNotNull null
            ProductRating(
                id = row.text("id", "entityId", "rating_id") ?: "rating-$index",
                uid = row.text("uid", "user_id"),
                username = username.orEmpty(),
                avatarUrl = row.text("userAvatar", "avatar", "user_avatar"),
                message = message,
                score = row.text("rating_score", "star", "score")?.toDoubleOrNull()?.toInt()?.takeIf { it in 1..5 },
                time = row.text("dateline_text", "datelineStr", "dateline")?.let(::plainText),
                device = row.text("device_title", "device"),
                pics = (row["picArr"] as? JsonArray ?: row["pics"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) },
            )
        }
        return ProductRatingPage(items = items, hasMore = rows.isNotEmpty())
    }

    fun parseChart(raw: String): ProductRatingChart {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return ProductRatingChart(emptyMap())
        val body = root["data"] as? JsonObject ?: root
        val periods = listOf("day", "week", "month").associateWith { key ->
            val period = body[key] as? JsonObject ?: return@associateWith emptyList()
            val chart = (period["ratingChart"] as? JsonObject) ?: (period["ownerRatingChart"] as? JsonObject)
            val points = (chart?.get("x") as? JsonArray).orEmpty().mapNotNull { element ->
                val point = element as? JsonObject ?: return@mapNotNull null
                val score = point.text("score")?.toDoubleOrNull() ?: return@mapNotNull null
                val count = point.text("count")?.toDoubleOrNull()?.toInt() ?: 0
                if (score <= 0 || score > 10 || count == 0) return@mapNotNull null
                RatingChartPoint(
                    label = point.text("datelineStr", "startDate", "dateline").orEmpty(),
                    score = score,
                    count = count,
                )
            }
            points
        }
        return ProductRatingChart(periods)
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun plainText(value: String): String =
        value.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
}
