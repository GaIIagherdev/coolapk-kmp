package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class AppDetail(
    val name: String,
    val packageName: String?,
    val iconUrl: String?,
    val developer: String?,
    val version: String?,
    val size: String?,
    val updatedAt: String?,
    val score: Double?,
    val downloadCount: String?,
    val description: String?,
    val changeLog: String?,
    val screenshots: List<String>,
)

class AppDetailJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parse(raw: String): AppDetail? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val data = root["data"] as? JsonObject ?: root
        val name = data.firstString("title", "appName", "apkName", "name") ?: return null
        val screenshots = data.firstValue("screenList", "screenshots", "screenArr", "screenshot", "screen")
            ?.imageUrls().orEmpty().distinct()
        return AppDetail(
            name = name,
            packageName = data.firstString("packageName", "apkPackageName", "package_name"),
            iconUrl = data.firstString("apkRomIcon", "logo", "icon", "apkLogo")?.imageUrl(),
            developer = data.firstString("developername", "developerName", "developer"),
            version = data.firstString("apkversionname", "versionName", "version"),
            size = data.firstString("apksize", "apkSizeFormatted", "size"),
            updatedAt = data.firstString("lastupdate", "lastUpdateFormatted", "update_time"),
            score = data.firstString("score", "rating")?.toDoubleOrNull(),
            downloadCount = data.firstString("downCountFormatted", "downCount", "downloadCount", "down_count"),
            description = data.firstString("introduce", "description", "apkDescription", "intro"),
            changeLog = data.firstString("changeLog", "changelog"),
            screenshots = screenshots,
        )
    }

    private fun JsonObject.firstString(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JsonObject.firstValue(vararg keys: String): JsonElement? =
        keys.firstNotNullOfOrNull { this[it] }

    private fun JsonElement.imageUrls(): List<String> = when (this) {
        is JsonArray -> flatMap { it.imageUrls() }
        is JsonObject -> listOfNotNull(
            firstString("sourceUrl", "source_url", "url", "pic", "imageUrl", "src")?.imageUrl(),
        )
        is JsonPrimitive -> contentOrNull.orEmpty().split(',').mapNotNull { it.imageUrl() }
    }

    private fun String.imageUrl(): String? {
        val value = trim()
        if (value.isEmpty() || value == "null" || value == "undefined") return null
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://", ignoreCase = true) -> "https://" + value.substring(7)
            value.startsWith("https://") -> value
            else -> "https://image.coolapk.com/${value.trimStart('/')}"
        }
    }
}
