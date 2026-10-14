package com.coolapk.kmp.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class DiscoveryTab(
    val key: String,
    val title: String,
    val url: String,
    val subTitle: String? = null,
    val iconUrl: String? = null,
    val webUrl: String? = null,
)

data class DiscoveryConfig(
    val tabs: List<DiscoveryTab>,
    val selectedKey: String? = tabs.firstOrNull()?.key,
)

data class DiscoveryCard(
    val id: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val url: String?,
    val entityType: String?,
    val feed: FeedItem? = null,
)

data class DiscoveryPage(
    val cards: List<DiscoveryCard>,
    val firstItem: String? = null,
    val lastItem: String? = null,
    val pageContext: String? = null,
    val hasMore: Boolean = cards.isNotEmpty(),
)

class DiscoveryJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val feedParser: FeedJsonParser = FeedJsonParser(),
) {
    fun parseConfig(raw: String): DiscoveryConfig {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return DiscoveryConfig(emptyList())
        val cards = (root["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val card = cards.firstOrNull {
            it.value("id", "entityId") == "20131" ||
                it.value("title")?.let { title -> title == "发现" || title.equals("discovery", true) } == true
        } ?: return DiscoveryConfig(emptyList())
        val tabs = mutableListOf<Pair<Int, DiscoveryTab>>()
        val seen = mutableSetOf<String>()
        fun visit(element: JsonElement?) {
            when (element) {
                is JsonArray -> element.forEach(::visit)
                is JsonObject -> {
                    val extra = element.extra()
                    val pageName = element.value("pageName", "page_name")
                        ?: extra.value("pageName", "page_name")
                    val webUrl = element.value("webUrl", "web_url")
                        ?: extra.value("webUrl", "web_url")
                    val url = element.value("url") ?: extra.value("url") ?: pageName ?: webUrl
                    val type = element.value("entityType", "entityTemplate").orEmpty()
                    val visibility = element.value("page_visibility", "pageVisibility")
                        ?: extra.value("page_visibility", "pageVisibility")
                    val status = element.value("status") ?: extra.value("status")
                    if ((type.contains("ConfigPage", true) || pageName != null) &&
                        !url.isNullOrBlank() &&
                        visibility !in setOf("0", "false") && status !in setOf("0", "false")
                    ) {
                        val title = element.value("title") ?: extra.value("title")
                        if (!title.isNullOrBlank()) {
                            val key = pageName ?: url
                            if (seen.add(key)) {
                                val order = element.value("order", "page_order")?.toIntOrNull()
                                    ?: extra.value("order")?.toIntOrNull() ?: tabs.size
                                tabs += order to DiscoveryTab(
                                    key = key,
                                    title = title,
                                    url = url,
                                    subTitle = element.value("subTitle", "sub_title")
                                        ?: extra.value("subTitle", "sub_title"),
                                    iconUrl = element.value("tabIcon", "tab_icon", "icon", "logo")
                                        ?.imageUrl(),
                                    webUrl = webUrl,
                                )
                            }
                        }
                    }
                    visit(element["entities"] ?: element["rawEntities"] ?: element["raw_entities"])
                }
                else -> Unit
            }
        }
        visit(card["entities"] ?: card["rawEntities"] ?: card["raw_entities"])
        val sorted = tabs.sortedBy { it.first }.map { it.second }
        val selected = card.extra().value("selectedHomeTab", "selectedTab")
            ?: card.value("selectedHomeTab", "selectedTab")
        return DiscoveryConfig(
            tabs = sorted,
            selectedKey = sorted.firstOrNull { it.key == selected || it.url == selected }?.key
                ?: sorted.firstOrNull()?.key,
        )
    }

    fun parsePage(raw: String): DiscoveryPage {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return DiscoveryPage(emptyList())
        val payload = root["data"] ?: return DiscoveryPage(emptyList())
        val rows = payload.rows()
        val cursorRows = (payload as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?.takeIf(List<JsonObject>::isNotEmpty) ?: rows
        val feedsById = feedParser.parseHomePage(raw).items.associateBy(FeedItem::id)
        val contentRows = rows.filterNot { row ->
            row.value("entityType")?.lowercase() in setOf("configcard", "sponsorcard") ||
                row.value("entityTemplate")?.lowercase() in setOf("configcard", "sponsorcard")
        }
        val cards = contentRows.mapIndexed { index, row ->
            val id = row.value("entityId", "entity_id", "id")
                ?: row.value("url") ?: "card-$index"
            val imageChild = ((row["entities"] as? JsonArray)?.singleOrNull() as? JsonObject)
                ?.takeIf { it.value("entityType")?.startsWith("image", true) == true }
            DiscoveryCard(
                id = id,
                title = row.value("title", "message_title", "name")
                    ?: imageChild?.value("title"),
                description = row.value("subTitle", "description", "message")
                    ?: imageChild?.value("description"),
                imageUrl = row.value("pic", "logo", "icon", "cover")?.imageUrl()
                    ?: (row["pics"] as? JsonArray)?.firstOrNull()?.imageUrl()
                    ?: imageChild?.value("pic")?.imageUrl(),
                url = row.value("url", "targetUrl", "target_url")
                    ?: imageChild?.value("url"),
                entityType = row.value("entityType", "entity_type", "entityTemplate"),
                feed = feedsById[row.value("id") ?: id],
            )
        }
        val pageInfo = root["pagination"] as? JsonObject
            ?: root["pageInfo"] as? JsonObject
            ?: root["page_info"] as? JsonObject
        val config = rows.firstOrNull {
            it.value("entityType")?.equals("configcard", true) == true ||
                it.value("entityTemplate")?.equals("configcard", true) == true
        }?.extra()
        val first = root.value("firstItem", "first_item") ?: pageInfo?.value("firstItem", "first_item")
            ?: cursorRows.firstOrNull()?.value("entityId", "entity_id", "id")
        val last = root.value("lastItem", "last_item") ?: pageInfo?.value("lastItem", "last_item")
            ?: cursorRows.lastOrNull()?.value("entityId", "entity_id", "id")
        val context = root.value("pageContext", "page_context")
            ?: pageInfo?.value("pageContext", "page_context")
            ?: config?.value("pageContext", "page_context")
            ?: contentRows.lastOrNull()?.value("pageContext", "page_context")
            ?: contentRows.lastOrNull()?.extra()?.value("pageContext", "page_context")
        val explicitMore = root.value("hasMore", "has_more")
            ?: pageInfo?.value("hasMore", "has_more")
        return DiscoveryPage(
            cards = cards,
            firstItem = first,
            lastItem = last,
            pageContext = context,
            hasMore = explicitMore?.equals("true", true) == true || explicitMore == "1" ||
                (explicitMore == null && contentRows.isNotEmpty()),
        )
    }

    private fun JsonElement.rows(): List<JsonObject> = when (this) {
        is JsonArray -> flatMap { it.rows() }
        is JsonObject -> {
            val children = this["entities"] ?: this["list"] ?: this["rows"] ?: this["data"]
            val isFeed = value("entityType", "entity_type")?.startsWith("feed", true) == true
            val isSelectorLinks = value("entityTemplate")?.equals("selectorLinkCard", true) == true
            if (children != null && (value("entityTemplate") == null && !isFeed || isSelectorLinks)) {
                children.rows()
            } else {
                listOf(this)
            }
        }
        else -> emptyList()
    }

    private fun JsonObject.extra(): JsonObject {
        val extra = this["extraData"] ?: this["extra_data"]
            ?: this["extraDataArr"] ?: this["extra_data_arr"]
        return when (extra) {
            is JsonObject -> extra
            is JsonPrimitive -> runCatching { json.parseToJsonElement(extra.content) as? JsonObject }
                .getOrNull() ?: JsonObject(emptyMap())
            else -> JsonObject(emptyMap())
        }
    }

    private fun JsonObject.value(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        }

    private fun String.imageUrl(): String? {
        val value = trim()
        return when {
            value.isEmpty() || value in setOf("null", "undefined") -> null
            value.startsWith("https://", true) -> value
            value.startsWith("http://", true) -> "https://${value.substring(7)}"
            value.startsWith("//") -> "https:$value"
            else -> "https://image.coolapk.com/${value.trimStart('/')}"
        }
    }

    private fun JsonElement.imageUrl(): String? = when (this) {
        is JsonObject -> value("sourceUrl", "url", "pic", "imageUrl")?.imageUrl()
        is JsonPrimitive -> contentOrNull?.imageUrl()
        else -> null
    }
}
