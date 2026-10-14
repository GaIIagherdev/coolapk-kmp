package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class DigitalEntity(
    val id: String,
    val title: String?,
    val subTitle: String?,
    val url: String?,
    val imageUrl: String?,
    val entityType: String?,
    val entityTemplate: String?,
    val entities: List<DigitalEntity>,
    val extraData: JsonObject,
    val requestArgs: JsonObject,
    val raw: JsonObject,
    val feed: FeedItem? = null,
)

data class DigitalTab(
    val key: String,
    val title: String,
    val url: String,
    val pageName: String? = null,
    val subTitle: String? = null,
    val webUrl: String? = null,
    val category: Boolean = false,
    val entities: List<DigitalEntity> = emptyList(),
    val requestArgs: JsonObject = JsonObject(emptyMap()),
    val raw: JsonObject = JsonObject(emptyMap()),
)

data class DigitalConfig(
    val tabs: List<DigitalTab>,
    val selectedKey: String? = tabs.firstOrNull()?.key,
)

data class DigitalPage(
    val items: List<DigitalEntity>,
    val firstItem: String? = null,
    val lastItem: String? = null,
    val pageContext: String? = null,
    val hasMore: Boolean = items.isNotEmpty(),
)

class DigitalJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val feedParser: FeedJsonParser = FeedJsonParser(),
) {
    fun parseConfig(raw: String): DigitalConfig {
        val root = json.parseToJsonElement(raw)
        val pages = mutableListOf<JsonObject>()
        fun visit(element: JsonElement?, digitalContext: Boolean = false) {
            when (element) {
                is JsonArray -> element.forEach { visit(it, digitalContext) }
                is JsonObject -> {
                    val inDigital = digitalContext || element.isDigitalContainer()
                    if (element.isDigitalPage() || digitalContext && element.isConfigPage()) pages += element
                    val childContext = inDigital && !element.isConfigPage()
                    if (!element.isConfigPage()) {
                        listOf(
                            "data", "entities", "rawEntities", "raw_entities", "pages", "tabs",
                            "configPages", "config_pages", "allConfigPages", "all_config_pages",
                        ).forEach { key -> visit(element[key].asStructured(), childContext) }
                    }
                }
                else -> Unit
            }
        }
        visit(root)

        val seen = mutableSetOf<String>()
        val tabs = pages.mapIndexedNotNull { index, page ->
            val extra = page.extra()
            val pageName = page.text("pageName", "page_name") ?: extra.text("pageName", "page_name")
            val rawUrl = page.text("url") ?: extra.text("url") ?: pageName
            val webUrl = page.text("webUrl", "web_url") ?: extra.text("webUrl", "web_url")
                ?: rawUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val url = rawUrl?.takeUnless { it.startsWith("http://") || it.startsWith("https://") }
                ?: pageName ?: webUrl ?: return@mapIndexedNotNull null
            val key = pageName ?: url
            if (!seen.add(key) || !page.visible(extra)) return@mapIndexedNotNull null
            val title = page.text("title") ?: extra.text("title") ?: return@mapIndexedNotNull null
            val children = page.children().mapIndexed { childIndex, child -> parseEntity(child, "$key-$childIndex", emptyMap()) }
            val order = (page.text("order", "page_order") ?: extra.text("order"))?.toIntOrNull() ?: index
            order to DigitalTab(
                key = key,
                title = title,
                url = url,
                pageName = pageName,
                subTitle = page.text("subTitle", "sub_title") ?: extra.text("subTitle", "sub_title"),
                webUrl = webUrl,
                category = page.text("category") == "1" ||
                    url.contains("/product/categoryList", true) ||
                    pageName?.contains("PRODUCT_LIST", true) == true,
                entities = children,
                requestArgs = page.requestArgs(),
                raw = page,
            )
        }.sortedBy { it.first }.map { it.second }

        val selected = findSelected(root)
        val duplicateDigital = tabs.withIndex().filter { it.value.title.trim() == "数码" }
        val redundantKey = if (duplicateDigital.size > 1) duplicateDigital.first().value.key else null
        val normalized = tabs.filterNot { it.key == redundantKey }
        val selectedKey = normalized.firstOrNull {
            it.key == selected || it.pageName == selected || it.url == selected || it.webUrl == selected
        }?.key ?: if (selected == redundantKey) normalized.firstOrNull { it.title.trim() == "数码" }?.key
        else null
        return DigitalConfig(normalized, selectedKey ?: normalized.firstOrNull()?.key)
    }

    fun parsePage(raw: String): DigitalPage {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return DigitalPage(emptyList(), hasMore = false)
        val payload = root["data"] ?: return DigitalPage(emptyList(), hasMore = false)
        val rows = payload.pageRows()
        val cursorRows = (payload as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.takeIf(List<JsonObject>::isNotEmpty) ?: rows
        val feeds = feedParser.parsePage(root).items.associateBy(FeedItem::id)
        val items = rows.filterNot { row ->
            row.text("entityType", "entity_type")?.equals("sponsorCard", true) == true ||
                row.text("entityTemplate", "entity_template")?.lowercase() in setOf("sponsorcard", "configcard")
        }.mapIndexed { index, row -> parseEntity(row, "digital-$index", feeds) }
        val pageInfo = (root["pagination"] ?: root["pageInfo"] ?: root["page_info"]) as? JsonObject
        val first = root.text("firstItem", "first_item") ?: pageInfo?.text("firstItem", "first_item")
            ?: cursorRows.firstOrNull()?.text("entityId", "entity_id", "id")
        val last = root.text("lastItem", "last_item") ?: pageInfo?.text("lastItem", "last_item")
            ?: cursorRows.lastOrNull()?.text("entityId", "entity_id", "id")
        val context = root.text("pageContext", "page_context") ?: pageInfo?.text("pageContext", "page_context")
            ?: cursorRows.firstOrNull { it.text("entityTemplate")?.equals("configCard", true) == true }
                ?.extra()?.text("pageContext", "page_context")
            ?: rows.lastOrNull()?.text("pageContext", "page_context")
        val explicitMore = root.text("hasMore", "has_more") ?: pageInfo?.text("hasMore", "has_more")
        return DigitalPage(
            items = items,
            firstItem = first,
            lastItem = last,
            pageContext = context,
            hasMore = when (explicitMore?.lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> items.isNotEmpty()
            },
        )
    }

    private fun parseEntity(row: JsonObject, fallbackId: String, feeds: Map<String, FeedItem>): DigitalEntity {
        val id = row.text("id", "entityId", "entity_id", "productId", "product_id") ?: fallbackId
        val children = row.children().mapIndexed { index, child -> parseEntity(child, "$id-$index", feeds) }
        val type = row.text("entityType", "entity_type")
        return DigitalEntity(
            id = id,
            title = row.text("title", "index_title", "name"),
            subTitle = row.text("subTitle", "sub_title", "description"),
            url = row.text("url", "targetUrl", "target_url"),
            imageUrl = row.text("logo", "pic", "cover", "icon", "product_logo")?.digitalImageUrl(),
            entityType = type,
            entityTemplate = row.text("entityTemplate", "entity_template"),
            entities = children,
            extraData = row.extra(),
            requestArgs = row.requestArgs(),
            raw = row,
            feed = if (type?.startsWith("feed", true) == true) feeds[id] else null,
        )
    }

    private fun JsonObject.isConfigPage(): Boolean {
        val type = text("entityType", "entity_type", "entityTemplate", "entity_template").orEmpty()
        return type.contains("ConfigPage", true) || text("pageName", "page_name") != null
    }

    private fun JsonObject.isDigitalContainer(): Boolean {
        val marker = listOfNotNull(text("title"), text("pageName", "page_name"), text("url"))
            .joinToString(" ").uppercase()
        return marker.contains("数码") || marker.contains("DIGITAL") ||
            marker.contains("PRODUCT") && text("entityTemplate")?.equals("configCard", true) == true
    }

    private fun JsonObject.isDigitalPage(): Boolean {
        val pageName = text("pageName", "page_name").orEmpty().uppercase()
        val url = text("url").orEmpty().lowercase()
        val title = text("title").orEmpty()
        if (!isConfigPage() && url.isEmpty()) return false
        return listOf("DIGITAL", "CHANNEL_SJB", "ZHUANTI_COMPUTER", "ZHUANTI_EARPHONE", "CHANNEL_SMB", "PRODUCT_LIST")
            .any(pageName::contains) || url == "/product/categorylist" || title.contains("数码") && (url.isNotEmpty() || pageName.isNotEmpty())
    }

    private fun JsonObject.visible(extra: JsonObject): Boolean = listOf(
        text("page_visibility", "pageVisibility", "visibility", "visible", "isVisible"),
        extra.text("page_visibility", "pageVisibility", "visibility", "visible", "isVisible"),
        text("status"), extra.text("status"),
    ).filterNotNull().none { it.lowercase() in setOf("0", "false", "hidden", "disabled") }

    private fun JsonObject.requestArgs(): JsonObject =
        sequenceOf(
            "requestArgs", "request_args", "requestParams", "request_params", "params",
            "queryParams", "query_params",
        ).mapNotNull { key -> (this[key] ?: extra()[key]).asStructured() as? JsonObject }
            .firstOrNull { it.isNotEmpty() }
            ?: (text("pageParam", "page_param") ?: extra().text("pageParam", "page_param"))
                ?.let { JsonObject(mapOf("pageParam" to JsonPrimitive(it))) }
            ?: JsonObject(emptyMap())

    private fun JsonObject.children(): List<JsonObject> =
        (this["entities"] ?: this["rawEntities"] ?: this["raw_entities"] ?: extra()["rawEntities"])
            .asStructured().let { it as? JsonArray }?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.extra(): JsonObject =
        (this["extraData"] ?: this["extra_data"] ?: this["extraDataArr"]).asStructured() as? JsonObject
            ?: JsonObject(emptyMap())

    private fun JsonElement?.asStructured(): JsonElement? = when (this) {
        is JsonPrimitive -> runCatching { json.parseToJsonElement(content) }.getOrNull()
        else -> this
    }

    private fun JsonElement.pageRows(): List<JsonObject> = when (this) {
        is JsonArray -> flatMap { it.pageRows() }
        is JsonObject -> {
            val type = text("entityTemplate", "entity_template")
            val children = this["data"] ?: this["list"] ?: this["rows"] ?: this["entities"]
            if (children != null && (type == null || type.equals("configCard", true))) children.pageRows()
            else listOf(this)
        }
        else -> emptyList()
    }

    private fun findSelected(element: JsonElement?): String? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull(::findSelected)
        is JsonObject -> {
            element.text(
                "selectedHomeTab", "selected_home_tab", "selectedTab", "selected_tab",
                "selectedPage", "selected_page", "selectedPageName", "selected_page_name",
            ) ?: element.extra().text("selectedHomeTab", "selectedTab")
                ?: listOf("data", "config", "navigation", "navigationServerData", "mainInit", "extraData")
                    .firstNotNullOfOrNull { findSelected(element[it].asStructured()) }
        }
        else -> null
    }

    private fun JsonObject.text(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    }
}

internal fun String.digitalImageUrl(): String? = when {
    isBlank() || this == "null" || this == "undefined" -> null
    startsWith("https://", true) -> this
    startsWith("http://", true) -> "https://${substring(7)}"
    startsWith("//") -> "https:$this"
    else -> "https://image.coolapk.com/${trimStart('/')}"
}

/** 产品列表项上的规格摘要（服务端 product_specs，数组或 "|" 分隔字符串）。 */
fun DigitalEntity.productSpecs(): List<String> = when (val value = raw["product_specs"] ?: raw["productSpecs"]) {
    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
    is JsonPrimitive -> value.contentOrNull?.split('|')?.map(String::trim).orEmpty()
    else -> emptyList()
}.filter(String::isNotEmpty)

/** 列表项自带的酷友评分（仅当服务端列表返回时），不额外请求详情。 */
fun DigitalEntity.listScore(): Double? {
    val text = (raw["owner_star_average_score"] ?: raw["rating_average_score"] ?: raw["score"])
        .let { (it as? JsonPrimitive)?.contentOrNull }
    return text?.toDoubleOrNull()?.takeIf { it > 0 }
}

/** 列表项自带的热度文案（仅当服务端列表返回时）。 */
fun DigitalEntity.listHeat(): String? =
    (raw["hot_num_txt"] ?: raw["hot_num"])?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf(String::isNotBlank)

/** 子栏目是否带请求参数（同一页面的不同筛选），与参考项目的判断一致。 */
val DigitalEntity.hasRequestArgs: Boolean get() = requestArgs.isNotEmpty()
