import Foundation

struct DiscoveryTab: Identifiable, Sendable {
    let key: String
    let title: String
    let url: String
    let subTitle: String?
    let iconURL: URL?
    let webURL: String?

    var id: String { key }
}

struct DiscoveryConfig: Sendable {
    let tabs: [DiscoveryTab]
    let selectedKey: String?
}

struct DiscoveryCard: Identifiable, Sendable {
    let id: String
    let title: String?
    let description: String?
    let imageURL: URL?
    let url: String?
    let entityType: String?
    let feedID: String?
}

struct DiscoveryPage: Sendable {
    let cards: [DiscoveryCard]
    let firstItem: String?
    let lastItem: String?
    let pageContext: String?
    let hasMore: Bool
}

enum DiscoveryParser {
    static func config(_ raw: String) -> DiscoveryConfig {
        guard let root = object(raw),
              let data = root["data"] as? [Any],
              let card = data.compactMap({ $0 as? [String: Any] }).first(where: {
                  text($0, ["id", "entityId"]) == "20131" ||
                      ["发现", "discovery"].contains(text($0, ["title"])?.lowercased() ?? "")
              }) else {
            return DiscoveryConfig(tabs: [], selectedKey: nil)
        }

        var orderedTabs: [(order: Int, tab: DiscoveryTab)] = []
        var seen = Set<String>()
        func visit(_ value: Any?) {
            if let array = value as? [Any] {
                array.forEach(visit)
                return
            }
            guard let row = value as? [String: Any] else { return }
            let extra = extraData(row)
            let pageName = text(row, ["pageName", "page_name"])
                ?? text(extra, ["pageName", "page_name"])
            let webURL = text(row, ["webUrl", "web_url"])
                ?? text(extra, ["webUrl", "web_url"])
            let url = text(row, ["url"]) ?? text(extra, ["url"]) ?? pageName ?? webURL
            let type = text(row, ["entityType", "entityTemplate"]) ?? ""
            let visibility = (text(row, ["page_visibility", "pageVisibility"])
                ?? text(extra, ["page_visibility", "pageVisibility"]))?.lowercased()
            let status = (text(row, ["status"]) ?? text(extra, ["status"]))?.lowercased()

            if (type.localizedCaseInsensitiveContains("ConfigPage") || pageName != nil),
               let url, !url.isEmpty,
               visibility != "0", visibility != "false",
               status != "0", status != "false",
               let title = text(row, ["title"]) ?? text(extra, ["title"]) {
                let key = pageName ?? url
                if seen.insert(key).inserted {
                    let order = Int(text(row, ["order", "page_order"])
                        ?? text(extra, ["order"]) ?? "") ?? orderedTabs.count
                    orderedTabs.append((order, DiscoveryTab(
                        key: key,
                        title: title,
                        url: url,
                        subTitle: text(row, ["subTitle", "sub_title"])
                            ?? text(extra, ["subTitle", "sub_title"]),
                        iconURL: imageURL(firstValue(row, ["tabIcon", "tab_icon", "icon", "logo"])
                            ?? firstValue(extra, ["tabIcon", "tab_icon", "icon", "logo"])),
                        webURL: webURL
                    )))
                }
            }
            visit(firstValue(row, ["entities", "rawEntities", "raw_entities"]))
        }
        visit(firstValue(card, ["entities", "rawEntities", "raw_entities"]))

        let tabs = orderedTabs.enumerated().sorted {
            $0.element.order == $1.element.order
                ? $0.offset < $1.offset : $0.element.order < $1.element.order
        }.map(\.element.tab)
        let selected = text(extraData(card), ["selectedHomeTab", "selectedTab"])
            ?? text(card, ["selectedHomeTab", "selectedTab"])
        return DiscoveryConfig(
            tabs: tabs,
            selectedKey: tabs.first(where: { $0.key == selected || $0.url == selected })?.key
                ?? tabs.first?.key
        )
    }

    static func page(_ raw: String) -> DiscoveryPage {
        guard let root = object(raw), let payload = root["data"] else {
            return DiscoveryPage(cards: [], firstItem: nil, lastItem: nil,
                                 pageContext: nil, hasMore: false)
        }
        let rows = contentRows(payload)
        let outerRows = (payload as? [Any])?.compactMap { $0 as? [String: Any] } ?? []
        let cursorRows = outerRows.isEmpty ? rows : outerRows
        let visibleRows = rows.filter { row in
            let type = text(row, ["entityType"])?.lowercased()
            let template = text(row, ["entityTemplate"])?.lowercased()
            return !["configcard", "sponsorcard"].contains(type ?? "")
                && !["configcard", "sponsorcard"].contains(template ?? "")
        }
        let cards = visibleRows.enumerated().map { index, row in
            let id = text(row, ["entityId", "entity_id", "id", "url"]) ?? "card-\(index)"
            let children = row["entities"] as? [Any] ?? []
            let onlyChild = children.count == 1 ? children.first as? [String: Any] : nil
            let imageChild = onlyChild.flatMap { child -> [String: Any]? in
                text(child, ["entityType"])?.lowercased().hasPrefix("image") == true ? child : nil
            }
            let picture = ["pic", "logo", "icon", "cover"]
                .lazy.compactMap { imageURL(row[$0]) }.first
                ?? (row["pics"] as? [Any])?.lazy.compactMap { imageURL($0) }.first
                ?? imageChild.flatMap { imageURL($0["pic"]) }
            return DiscoveryCard(
                id: id,
                title: text(row, ["title", "message_title", "name"])
                    ?? imageChild.flatMap { text($0, ["title"]) },
                description: text(row, ["subTitle", "description", "message"])
                    ?? imageChild.flatMap { text($0, ["description"]) },
                imageURL: picture,
                url: text(row, ["url", "targetUrl", "target_url"])
                    ?? imageChild.flatMap { text($0, ["url"]) },
                entityType: text(row, ["entityType", "entity_type", "entityTemplate"]),
                feedID: isFeed(row) ? text(row, ["id", "entityId", "entity_id"]) : nil
            )
        }
        let pageInfo = firstValue(root, ["pagination", "pageInfo", "page_info"]) as? [String: Any]
        let config = rows.first(where: {
            text($0, ["entityType"])?.lowercased() == "configcard"
                || text($0, ["entityTemplate"])?.lowercased() == "configcard"
        }).map(extraData)
        let lastContent = visibleRows.last
        let context = text(root, ["pageContext", "page_context"])
            ?? pageInfo.flatMap { text($0, ["pageContext", "page_context"]) }
            ?? config.flatMap { text($0, ["pageContext", "page_context"]) }
            ?? lastContent.flatMap { text($0, ["pageContext", "page_context"]) }
            ?? lastContent.flatMap { text(extraData($0), ["pageContext", "page_context"]) }
        let more = boolean(root, ["hasMore", "has_more"])
            ?? pageInfo.flatMap { boolean($0, ["hasMore", "has_more"]) }
            ?? !visibleRows.isEmpty
        return DiscoveryPage(
            cards: cards,
            firstItem: text(root, ["firstItem", "first_item"])
                ?? pageInfo.flatMap { text($0, ["firstItem", "first_item"]) }
                ?? cursorRows.first.flatMap { text($0, ["entityId", "entity_id", "id"]) },
            lastItem: text(root, ["lastItem", "last_item"])
                ?? pageInfo.flatMap { text($0, ["lastItem", "last_item"]) }
                ?? cursorRows.last.flatMap { text($0, ["entityId", "entity_id", "id"]) },
            pageContext: context,
            hasMore: more
        )
    }

    private static func contentRows(_ value: Any) -> [[String: Any]] {
        if let array = value as? [Any] { return array.flatMap(contentRows) }
        guard let row = value as? [String: Any] else { return [] }
        let children = firstValue(row, ["entities", "list", "rows", "data"])
        let type = text(row, ["entityType", "entity_type"])?.lowercased() ?? ""
        let isFeed = type.hasPrefix("feed")
        let selector = text(row, ["entityTemplate"])?.lowercased() == "selectorlinkcard"
        if let children,
           (text(row, ["entityTemplate"]) == nil && !isFeed || selector) {
            return contentRows(children)
        }
        return [row]
    }

    private static func isFeed(_ row: [String: Any]) -> Bool {
        guard text(row, ["entityId", "id"]) != nil else { return false }
        if let type = text(row, ["entityType", "entity_type"])?.lowercased() {
            return ["feed", "feedreply", "article", "news", "dyh", "question", "answer"].contains(type)
                || type.hasPrefix("feed_")
        }
        return text(row, ["message", "messageRaw"]) != nil
            || (text(row, ["title"]) != nil && text(row, ["username"]) != nil)
    }

    private static func object(_ raw: String) -> [String: Any]? {
        guard let data = raw.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    private static func extraData(_ row: [String: Any]) -> [String: Any] {
        let value = firstValue(row, ["extraData", "extra_data", "extraDataArr", "extra_data_arr"])
        if let object = value as? [String: Any] { return object }
        if let string = value as? String { return object(string) ?? [:] }
        return [:]
    }

    private static func firstValue(_ row: [String: Any], _ keys: [String]) -> Any? {
        keys.lazy.compactMap { row[$0] }.first
    }

    private static func text(_ row: [String: Any], _ keys: [String]) -> String? {
        keys.lazy.compactMap { key -> String? in
            let value = row[key]
            let result: String
            if let string = value as? String { result = string }
            else if let number = value as? NSNumber { result = number.stringValue }
            else { return nil }
            let trimmed = result.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty || trimmed == "null" ? nil : trimmed
        }.first
    }

    private static func boolean(_ row: [String: Any], _ keys: [String]) -> Bool? {
        switch text(row, keys)?.lowercased() {
        case "true", "1": true
        case "false", "0": false
        default: nil
        }
    }

    private static func imageURL(_ value: Any?) -> URL? {
        let raw: String?
        if let row = value as? [String: Any] {
            raw = text(row, ["sourceUrl", "url", "pic", "imageUrl"])
        } else if let string = value as? String {
            raw = string
        } else {
            raw = nil
        }
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty, raw != "null", raw != "undefined" else { return nil }
        if raw.lowercased().hasPrefix("https://") { return URL(string: raw) }
        if raw.lowercased().hasPrefix("http://") { return URL(string: "https://" + raw.dropFirst(7)) }
        if raw.hasPrefix("//") { return URL(string: "https:" + raw) }
        return URL(string: "https://image.coolapk.com/" + raw.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }
}
