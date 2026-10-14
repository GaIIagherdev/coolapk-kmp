package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class DigitalProductDetail(
    val id: String,
    val title: String,
    val logoUrl: String?,
    val description: String?,
    val brand: String?,
    val category: String?,
    val score: Double?,
    val ratingCount: Int?,
    val hotCount: String?,
    val specs: List<String>,
    val imageUrls: List<String>,
    val raw: JsonObject,
)

class DigitalProductDetailJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parse(raw: String, fallbackId: String? = null): DigitalProductDetail? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val body = root["data"] as? JsonObject ?: return null
        val id = body.text("id", "product_id", "productId", "entityId") ?: fallbackId ?: return null
        val title = body.text("title", "index_title", "alias_title", "name") ?: return null
        val specs = when (val value = body["product_specs"] ?: body["productSpecs"]) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            is JsonPrimitive -> value.contentOrNull?.split('|')?.map(String::trim).orEmpty()
            else -> emptyList()
        }.filter(String::isNotEmpty)
        val imageUrls = sequenceOf("imageUriList", "image_uri_list", "picArr", "pics", "images")
            .mapNotNull { key -> (body[key] as? JsonArray)?.mapNotNull { image -> image.imageUrl() } }
            .firstOrNull { it.isNotEmpty() }.orEmpty()
        return DigitalProductDetail(
            id = id,
            title = title,
            logoUrl = body.text("logo", "product_logo", "pic", "icon")?.digitalImageUrl(),
            description = body.text("description", "device_info", "subTitle", "sub_title"),
            brand = body.text("brand_name", "brandName", "brand")
                ?: (body["brandInfo"] as? JsonObject)?.text("name", "title"),
            category = body.text("category_name", "categoryName", "category")
                ?: (body["categoryInfo"] as? JsonObject)?.text("name", "title"),
            score = body.text("owner_star_average_score", "rating_average_score")?.toDoubleOrNull(),
            ratingCount = body.text("owner_rating_total_num", "owner_star_total_count", "rating_total_num", "star_total_count")?.toIntOrNull(),
            hotCount = body.text("hot_num_txt", "hot_num", "hotNum"),
            specs = specs,
            imageUrls = imageUrls,
            raw = body,
        )
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun JsonElement.imageUrl(): String? = when (this) {
        is JsonPrimitive -> contentOrNull?.digitalImageUrl()
        is JsonObject -> text("sourceUrl", "url", "pic", "imageUrl")?.digitalImageUrl()
        else -> null
    }
}
