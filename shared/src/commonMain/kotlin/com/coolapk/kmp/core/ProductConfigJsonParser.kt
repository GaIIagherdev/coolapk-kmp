package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 一款产品的某个型号配置，用于对比。 */
data class ProductConfigItem(
    val id: String,
    val title: String,
    val baseFields: Map<String, String>,
    /** 分组名 -> (字段名 -> 值)，对应参考项目 config_data 的结构。 */
    val groups: Map<String, Map<String, String>>,
)

/** 对比表的一行：字段名、标签与每个型号的值。 */
data class CompareRow(val key: String, val label: String, val values: List<String>)

data class CompareSection(val title: String, val rows: List<CompareRow>)

class ProductConfigJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    fun parse(raw: String, id: String): ProductConfigItem? {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
        val data = root["data"] as? JsonObject ?: return null
        val base = BASE_FIELDS.mapNotNull { key ->
            text(data, key)?.let { key to it }
        }.toMap()
        return ProductConfigItem(
            id = id,
            title = text(data, "title") ?: "配置",
            baseFields = base,
            groups = parseGroups(text(data, "config_data")),
        )
    }

    /** 按参考项目的规则生成对比分组：基础信息（有值的字段）+ 各参数分组中首次出现的字段。 */
    fun buildSections(items: List<ProductConfigItem>): List<CompareSection> {
        val sections = mutableListOf<CompareSection>()
        val basicRows = BASE_FIELDS.mapNotNull { key ->
            val values = items.map { it.baseFields[key].orEmpty() }
            if (values.any(String::isNotBlank)) CompareRow(key, BASE_LABELS[key] ?: key, values) else null
        }
        if (basicRows.isNotEmpty()) sections += CompareSection("基础信息", basicRows)

        val seen = BASE_FIELDS.toMutableSet()
        val groupNames = items.flatMap { it.groups.keys }.distinct()
        for (group in groupNames) {
            val fieldNames = items.flatMap { it.groups[group]?.keys.orEmpty() }.distinct()
            val rows = fieldNames.mapNotNull { field ->
                val key = "$group.$field"
                if (!seen.add(key)) return@mapNotNull null
                val values = items.map { it.groups[group]?.get(field).orEmpty() }
                if (values.any(String::isNotBlank)) CompareRow(key, field, values) else null
            }
            if (rows.isNotEmpty()) sections += CompareSection(group, rows)
        }
        return sections
    }

    private fun parseGroups(raw: String?): Map<String, Map<String, String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (group, value) ->
            val fields = value as? JsonObject ?: return@mapNotNull null
            val parsed = fields.mapNotNull { (name, field) ->
                val text = when (field) {
                    is JsonPrimitive -> field.contentOrNull
                    is JsonArray -> field.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("，")
                    else -> null
                }?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                name to text
            }.toMap()
            if (parsed.isEmpty()) null else group to parsed
        }.toMap()
    }

    private fun text(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private companion object {
        val BASE_FIELDS = listOf("release_time", "cpu", "ram", "phone_material", "screen_material", "keywords")
        val BASE_LABELS = mapOf(
            "release_time" to "发布时间",
            "cpu" to "处理器",
            "ram" to "运行内存",
            "phone_material" to "机身材料",
            "screen_material" to "屏幕材质",
            "keywords" to "关键词",
        )
    }
}
