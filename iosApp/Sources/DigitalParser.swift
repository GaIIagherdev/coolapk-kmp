import Foundation

struct DigitalTab: Identifiable, Sendable {
    let key: String
    let title: String
    let url: String
    let pageName: String?
    let subTitle: String?
    let webURL: String?
    let category: Bool
    let rawEntities: [DigitalEntity]
    let requestArgsJSON: String

    var id: String { key }
}

struct DigitalConfig: Sendable {
    let tabs: [DigitalTab]
    let selectedKey: String?
}

struct DigitalEntity: Identifiable, Sendable {
    let id: String
    let entityType: String?
    let entityTemplate: String?
    let title: String?
    let subTitle: String?
    let imageURL: URL?
    let url: String?
    let productID: String?
    let brandType: String?
    let productSpecs: [String]
    let hot: String?
    let release: String?
    let price: String?
    let rating: String?
    let extra: [String: AnySendable]
    let entities: [DigitalEntity]
}

// JSONSerialization values are not Sendable. The parser only needs a small,
// value-like subset of extraData for grid columns and ranking.
enum AnySendable: Sendable, Equatable {
    case string(String)
    case number(Double)
    case bool(Bool)

    var stringValue: String? {
        switch self {
        case .string(let value): value
        case .number(let value): String(value)
        case .bool(let value): String(value)
        }
    }
}

struct DigitalPage: Sendable {
    let items: [DigitalEntity]
    let firstItem: String?
    let lastItem: String?
    let pageContext: String?
    let hasMore: Bool
}

enum DigitalParser {
    static func config(_ raw: String) -> DigitalConfig {
        guard let root = object(raw) else { return DigitalConfig(tabs: [], selectedKey: nil) }
        var rows: [[String: Any]] = []
        walkConfig(root, into: &rows, allowPageName: false)
        var seen = Set<String>()
        let tabs = rows.enumerated().compactMap { index, row -> (Int, Int, DigitalTab)? in
            let extra = rawExtraData(row)
            let pageName = text(row, ["pageName", "page_name"]) ?? text(extra, ["pageName", "page_name"])
            let rawURL = text(row, ["url"]) ?? text(extra, ["url"]) ?? pageName
            let webURL = text(row, ["webUrl", "web_url"]) ?? text(extra, ["webUrl", "web_url"])
                ?? ((rawURL?.hasPrefix("http") == true) ? rawURL : nil)
            let url = rawURL.flatMap { $0.hasPrefix("http") ? pageName ?? webURL : $0 } ?? webURL
            guard let url, !url.isEmpty, visible(row, extra: rawExtraData(row)) else { return nil }
            let key = pageName ?? url
            guard seen.insert(key).inserted else { return nil }
            let title = text(row, ["title"]) ?? text(extra, ["title"]) ?? "数码"
            let order = Int(text(row, ["order", "page_order"]) ?? text(extra, ["order"]) ?? "") ?? index
            let category = bool(row, ["category"]) == true || bool(extra, ["category"]) == true
                || url.localizedCaseInsensitiveContains("/product/categorylist")
                || (pageName?.localizedCaseInsensitiveContains("PRODUCT_LIST") == true)
            return (order, index, DigitalTab(
                key: key, title: title, url: url, pageName: pageName,
                subTitle: text(row, ["subTitle", "sub_title"]) ?? text(extra, ["subTitle", "sub_title"]),
                webURL: webURL, category: category,
                rawEntities: entities(firstValue(row, ["entities", "rawEntities", "raw_entities"])),
                requestArgsJSON: requestArgsJSON(row)
            ))
        }.sorted { $0.0 == $1.0 ? $0.1 < $1.1 : $0.0 < $1.0 }.map(\.2)
        let duplicateDigital = tabs.filter { $0.title.trimmingCharacters(in: .whitespaces) == "数码" }
        let redundantKey = duplicateDigital.count > 1 ? duplicateDigital.first?.key : nil
        let digitalTabs = tabs.filter { $0.key != redundantKey }
        let selected = findSelected(root)
        return DigitalConfig(
            tabs: digitalTabs,
            selectedKey: digitalTabs.first(where: { $0.key == selected || $0.url == selected || $0.pageName == selected || $0.webURL == selected })?.key
                ?? digitalTabs.first?.key
        )
    }

    static func page(_ raw: String) -> DigitalPage {
        guard let root = object(raw), let payload = root["data"] else {
            return DigitalPage(items: [], firstItem: nil, lastItem: nil, pageContext: nil, hasMore: false)
        }
        let rows = contentRows(payload)
        let cursorRows = (payload as? [Any])?.compactMap { $0 as? [String: Any] } ?? rows
        let info = (root["pagination"] ?? root["pageInfo"] ?? root["page_info"]) as? [String: Any]
        let items = rows.filter { row in
            let type = (text(row, ["entityType", "entity_type"]) ?? "").lowercased()
            let template = (text(row, ["entityTemplate", "entity_template"]) ?? "").lowercased()
            return type != "sponsorcard" && template != "sponsorcard" && template != "configcard"
        }
        let more = bool(root, ["hasMore", "has_more"]) ?? info.flatMap { bool($0, ["hasMore", "has_more"]) } ?? !items.isEmpty
        return DigitalPage(
            items: items.enumerated().map { entity($0.1, fallbackID: "digital-\($0.0)") },
            firstItem: text(root, ["firstItem", "first_item"]) ?? info.flatMap { text($0, ["firstItem", "first_item"]) }
                ?? cursorRows.first.flatMap { text($0, ["entityId", "entity_id", "id"]) },
            lastItem: text(root, ["lastItem", "last_item"]) ?? info.flatMap { text($0, ["lastItem", "last_item"]) }
                ?? cursorRows.last.flatMap { text($0, ["entityId", "entity_id", "id"]) },
            pageContext: text(root, ["pageContext", "page_context"]) ?? info.flatMap { text($0, ["pageContext", "page_context"]) }
                ?? cursorRows.first(where: { text($0, ["entityTemplate", "entity_template"])?.lowercased() == "configcard" })
                    .flatMap { text(rawExtraData($0), ["pageContext", "page_context"]) }
                ?? rows.last.flatMap { text($0, ["pageContext", "page_context"]) },
            hasMore: more
        )
    }

    private static func walkConfig(_ value: Any, into rows: inout [[String: Any]], allowPageName: Bool) {
        if let array = value as? [Any] { array.forEach { walkConfig($0, into: &rows, allowPageName: allowPageName) }; return }
        guard let row = value as? [String: Any] else { return }
        let digitalContext = allowPageName || isDigitalContainer(row)
        if isDigitalPage(row) || (allowPageName && isConfigPage(row)) { rows.append(row) }
        guard !isConfigPage(row) else { return }
        for key in ["data", "entities", "configPages", "config_pages", "allConfigPages", "all_config_pages", "tabs", "pages", "rawEntities", "raw_entities"] {
            if let nested = row[key].flatMap(structured) {
                walkConfig(nested, into: &rows, allowPageName: digitalContext)
            }
        }
    }

    private static func contentRows(_ value: Any) -> [[String: Any]] {
        if let array = value as? [Any] { return array.flatMap(contentRows) }
        guard let row = value as? [String: Any] else { return [] }
        let children = firstValue(row, ["data", "list", "rows", "entities"])
        let template = (text(row, ["entityTemplate", "entity_template"]) ?? "").lowercased()
        if let children, template.isEmpty || template == "configcard" { return contentRows(children) }
        return [row]
    }

    private static func entity(_ row: [String: Any], fallbackID: String) -> DigitalEntity {
        let extra = extraData(row)
        let children = entities(firstValue(row, ["entities", "list", "rows", "data"]))
        let id = text(row, ["id", "entityId", "entity_id", "productId", "product_id", "url"]) ?? fallbackID
        let specs = arrayText(row, ["productSpecs", "product_specs", "tagArr", "tag_arr"])
        return DigitalEntity(
            id: id,
            entityType: text(row, ["entityType", "entity_type"]),
            entityTemplate: text(row, ["entityTemplate", "entity_template"]),
            title: text(row, ["title", "productTitle", "product_title", "name", "label"]),
            subTitle: text(row, ["subTitle", "sub_title", "description", "categoryName", "category_name"]),
            imageURL: imageURL(firstValue(row, ["logo", "compatLogo", "compat_logo", "pic", "picUrl", "pic_url", "icon", "image", "cover", "product_logo"])),
            url: text(row, ["url", "targetUrl", "target_url"]),
            productID: text(row, ["productId", "product_id"]),
            brandType: text(row, ["type"]),
            productSpecs: specs,
            hot: text(row, ["hotNumText", "hot_num_txt", "followNumText", "follow_num_txt", "hotNum", "hot_num"]),
            release: text(row, ["releaseTime", "release_time", "releaseDate", "release_date"]),
            price: price(row),
            rating: text(row, ["ratingAverageScore", "rating_average_score", "starAverageScore", "star_average_score"]),
            extra: extra,
            entities: children
        )
    }

    private static func entities(_ value: Any?) -> [DigitalEntity] {
        if let array = value as? [Any] { return array.enumerated().compactMap { row in (row.element as? [String: Any]).map { entity($0, fallbackID: "entity-\(row.offset)") } } }
        if let parsed = value.flatMap(structured), !(parsed is String) { return entities(parsed) }
        return []
    }

    private static func isConfigPage(_ row: [String: Any]) -> Bool {
        let type = text(row, ["entityType", "entity_type", "entityTemplate", "entity_template"]) ?? ""
        return type.localizedCaseInsensitiveContains("configPage") || text(row, ["pageName", "page_name"]) != nil
    }

    private static func isDigitalContainer(_ row: [String: Any]) -> Bool {
        let marker = [text(row, ["title"]), text(row, ["pageName", "page_name"]), text(row, ["url"])]
            .compactMap { $0 }.joined(separator: " ").uppercased()
        return marker.contains("数码") || marker.contains("DIGITAL")
            || marker.contains("PRODUCT") && text(row, ["entityTemplate"])?.lowercased() == "configcard"
    }

    private static func isDigitalPage(_ row: [String: Any]) -> Bool {
        let pageName = (text(row, ["pageName", "page_name"]) ?? "").uppercased()
        let url = (text(row, ["url"]) ?? "").lowercased()
        let title = text(row, ["title"]) ?? ""
        if !isConfigPage(row) && url.isEmpty { return false }
        let markers = ["DIGITAL", "CHANNEL_SJB", "ZHUANTI_COMPUTER", "ZHUANTI_EARPHONE", "CHANNEL_SMB", "PRODUCT_LIST"]
        return markers.contains(where: pageName.contains) || url == "/product/categorylist"
            || title.contains("数码") && (!url.isEmpty || !pageName.isEmpty)
    }

    private static func visible(_ row: [String: Any], extra: [String: Any]) -> Bool {
        let flags = ["page_visibility", "pageVisibility", "visibility", "visible", "isVisible", "status"]
        let hidden = ["0", "false", "hidden", "disabled"]
        return flags.allSatisfy { key in
            let value = text(row, [key]) ?? text(extra, [key])
            return value.map { !hidden.contains($0.lowercased()) } ?? true
        }
    }

    private static func requestArgsJSON(_ row: [String: Any]) -> String {
        let extra = rawExtraData(row)
        for key in ["requestArgs", "request_args", "requestParams", "request_params", "params", "queryParams", "query_params"] {
            if let value = row[key] ?? extra[key], let object = structured(value) as? [String: Any], !object.isEmpty,
               JSONSerialization.isValidJSONObject(object),
               let data = try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]),
               let json = String(data: data, encoding: .utf8) { return json }
        }
        if let pageParam = text(row, ["pageParam", "page_param"]) ?? text(extra, ["pageParam", "page_param"]),
           let data = try? JSONSerialization.data(withJSONObject: ["pageParam": pageParam], options: [.sortedKeys]),
           let json = String(data: data, encoding: .utf8) { return json }
        return "{}"
    }

    private static func extraData(_ row: [String: Any]) -> [String: AnySendable] {
        rawExtraData(row).compactMapValues { value in
            if let string = value as? String { return .string(string) }
            if let number = value as? NSNumber { return .number(number.doubleValue) }
            return nil
        }
    }

    private static func rawExtraData(_ row: [String: Any]) -> [String: Any] {
        firstValue(row, ["extraData", "extra_data", "extraDataArr", "extra_data_arr"])
            .flatMap(structured) as? [String: Any] ?? [:]
    }

    private static func findSelected(_ value: Any) -> String? {
        if let array = value as? [Any] { return array.lazy.compactMap { findSelected($0) }.first }
        guard let row = value as? [String: Any] else { return nil }
        if let selected = text(row, ["selectedHomeTab", "selectedTab", "selectedPage", "selectedPageName"]) { return selected }
        for key in ["data", "extraData", "extra_data", "mainInit", "main_init"] {
            if let nested = row[key] {
                let parsed: Any = key == "extraData" || key == "extra_data" ? (object(nested as? String ?? "") ?? nested) : nested
                if let selected = findSelected(parsed) { return selected }
            }
        }
        return nil
    }

    private static func object(_ raw: String) -> [String: Any]? {
        guard let data = raw.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    private static func structured(_ value: Any) -> Any? {
        guard let string = value as? String else { return value }
        guard let data = string.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data)
    }

    private static func firstValue(_ row: [String: Any], _ keys: [String]) -> Any? { keys.lazy.compactMap { row[$0] }.first }

    private static func text(_ row: [String: Any], _ keys: [String]) -> String? {
        keys.lazy.compactMap { key in
            guard let value = row[key], !(value is NSNull) else { return nil }
            if let string = value as? String { return string.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty }
            if let number = value as? NSNumber { return number.stringValue }
            return nil
        }.first
    }

    private static func bool(_ row: [String: Any], _ keys: [String]) -> Bool? {
        switch text(row, keys)?.lowercased() { case "true", "1": true; case "false", "0": false; default: nil }
    }

    private static func arrayText(_ row: [String: Any], _ keys: [String]) -> [String] {
        guard let value = firstValue(row, keys) else { return [] }
        if let array = value as? [Any] { return array.compactMap { ($0 as? String) ?? ($0 as? NSNumber)?.stringValue }.filter { !$0.isEmpty } }
        return (value as? String)?.split(whereSeparator: { "|,，、·".contains($0) }).map(String.init).filter { !$0.isEmpty } ?? []
    }

    private static func price(_ row: [String: Any]) -> String? {
        let currency = text(row, ["priceCurrency", "price_currency"]) ?? "¥"
        guard let min = Double(text(row, ["priceMin", "price_min"]) ?? "") ?? nil,
              min > 0 else { return nil }
        let max = Double(text(row, ["priceMax", "price_max"]) ?? "") ?? min
        let format: (Double) -> String = { value in
            guard value.rounded() != value else { return String(Int(value)) }
            return String(format: "%.2f", value)
                .replacingOccurrences(of: #"\.?0+$"#, with: "", options: .regularExpression)
        }
        return min == max ? "\(currency)\(format(min))" : "\(currency)\(format(min))-\(format(max))"
    }

    private static func imageURL(_ value: Any?) -> URL? {
        let raw: String?
        if let row = value as? [String: Any] { raw = text(row, ["sourceUrl", "source_url", "url", "pic", "imageUrl"]) }
        else { raw = value as? String }
        guard let value = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return nil }
        if value.hasPrefix("http://") { return URL(string: "https://" + value.dropFirst(7)) }
        if value.hasPrefix("https://") { return URL(string: value) }
        if value.hasPrefix("//") { return URL(string: "https:" + value) }
        return URL(string: "https://image.coolapk.com/" + value.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
