import Foundation

enum SearchCategory: String, CaseIterable, Identifiable, Sendable {
    case all
    case apk
    case game
    case user
    case feed
    case ask
    case product
    case feedTopic

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: "综合"
        case .apk: "应用"
        case .game: "游戏"
        case .user: "用户"
        case .feed: "动态"
        case .ask: "问答"
        case .product: "数码"
        case .feedTopic: "话题"
        }
    }
}

enum SearchResultKind: String, Sendable {
    case feed
    case app
    case game
    case product
    case user
    case question
    case topic
    case other

    var title: String {
        switch self {
        case .feed: "动态"
        case .app: "应用"
        case .game: "游戏"
        case .product: "数码"
        case .user: "用户"
        case .question: "问答"
        case .topic: "话题"
        case .other: "内容"
        }
    }

    var symbol: String {
        switch self {
        case .feed: "text.bubble"
        case .app: "square.grid.2x2"
        case .game: "gamecontroller"
        case .product: "cpu"
        case .user: "person.crop.circle"
        case .question: "questionmark.bubble"
        case .topic: "number"
        case .other: "doc.text"
        }
    }
}

struct SearchResult: Identifiable, Sendable {
    let id: String
    let kind: SearchResultKind
    let title: String
    let subtitle: String?
    let imageURL: URL?
    let targetID: String?

    var canOpen: Bool {
        guard let targetID, !targetID.isEmpty else { return false }
        return kind == .feed || kind == .app || kind == .game || kind == .product
    }
}

struct SearchPage: Sendable {
    let items: [SearchResult]
    let firstItem: String?
    let lastItem: String?
    let hasMore: Bool
}

enum SearchParser {
    static func page(_ raw: String, category: SearchCategory) -> SearchPage {
        let root = object(raw)
        let payload = root["data"]
        let rows = collectRows(payload)
        var seen = Set<String>()
        let items = rows.enumerated().compactMap { index, row -> SearchResult? in
            guard let result = result(row, category: category, index: index) else { return nil }
            return seen.insert(result.id).inserted ? result : nil
        }
        let fields = pageFields(root, payload)
        let cursorRows = (payload as? [Any])?.compactMap { $0 as? [String: Any] } ?? rows
        let first = fields.lazy.compactMap { text($0, ["firstItem", "first_item"]) }.first
            ?? cursorRows.first.flatMap { text($0, ["entityId", "entity_id", "id"]) }
        let last = fields.lazy.compactMap { text($0, ["lastItem", "last_item"]) }.first
            ?? cursorRows.last.flatMap { text($0, ["entityId", "entity_id", "id"]) }
        let hasMore = fields.lazy.compactMap { bool($0, ["hasMore", "has_more"]) }.first ?? !rows.isEmpty
        return SearchPage(items: items, firstItem: first, lastItem: last, hasMore: hasMore)
    }

    static func hotWords(_ raw: String) -> [String] {
        let root = object(raw)
        var values: [String] = []
        var seen = Set<String>()
        func visit(_ value: Any?) {
            if let array = value as? [Any] { array.forEach(visit); return }
            if let word = value as? String {
                let trimmed = word.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty, seen.insert(trimmed).inserted { values.append(trimmed) }
                return
            }
            guard let row = value as? [String: Any], !isSponsor(row) else { return }
            let children = ["entities", "entityList", "data", "list", "items", "children"]
                .compactMap { row[$0] as? [Any] }.flatMap { $0 }
            if !children.isEmpty { children.forEach(visit); return }
            visit(text(row, ["title", "searchValue", "keyword", "word", "name"]))
        }
        visit(root["data"])
        return values
    }

    private static func result(_ row: [String: Any], category: SearchCategory, index: Int) -> SearchResult? {
        guard !isSponsor(row) else { return nil }
        let type = (text(row, ["entityType", "entity_type", "type"]) ?? "").lowercased()
        let template = (text(row, ["entityTemplate", "entity_template", "template"]) ?? "").lowercased()
        if template.contains("searchhot") || type.contains("searchhot") { return nil }
        let feedType = (text(row, ["feedType", "feed_type"]) ?? "").lowercased()
        let package = text(row, ["packageName", "package_name", "pkg", "apkname"])
        let kind: SearchResultKind
        if type.contains("user") || template.contains("user") || category == .user {
            kind = .user
        } else if type.contains("topic") || template.contains("topic") || category == .feedTopic {
            kind = .topic
        } else if type.contains("game") || category == .game {
            kind = .game
        } else if type.contains("apk") || type.contains("app") || package != nil || category == .apk {
            kind = .app
        } else if type.contains("product") || template.contains("product") || category == .product {
            kind = .product
        } else if type.contains("answer") || feedType.contains("answer") {
            kind = .feed
        } else if type.contains("question") || template.contains("question")
                    || text(row, ["questionId", "question_id"]) != nil || category == .ask {
            kind = .question
        } else if type.contains("feed") || type.contains("article") || type.contains("reply")
                    || text(row, ["message", "messageRaw"]) != nil || category == .feed {
            kind = .feed
        } else {
            kind = .other
        }

        let rawID = text(row, ["id", "entityId", "entity_id", "feedId", "feed_id", "uid"])
        let targetID: String?
        switch kind {
        case .app, .game: targetID = package
        case .product: targetID = text(row, ["productId", "product_id"]) ?? rawID
        case .feed: targetID = rawID
        default: targetID = nil
        }
        let title = text(row, ["title", "message_title", "messageTitle", "questionTitle", "shorttitle",
                                "appName", "apkName", "name", "username", "entityTitle", "label", "searchValue", "message"])
            ?? kind.title
        let subtitle = text(row, ["subTitle", "subtitle", "description", "message", "summary", "bio", "sign"])
        let image = text(row, ["apkRomIcon", "logo", "icon", "pic", "userAvatar", "cover", "image", "avatar"])
        return SearchResult(
            id: "\(kind.rawValue):\(rawID ?? targetID ?? "\(index)-\(title)")",
            kind: kind, title: title, subtitle: subtitle == title ? nil : subtitle,
            imageURL: imageURL(image), targetID: targetID
        )
    }

    private static func collectRows(_ value: Any?) -> [[String: Any]] {
        if let array = value as? [Any] { return array.flatMap(collectRows) }
        guard let row = value as? [String: Any], !isSponsor(row) else { return [] }
        let type = (text(row, ["entityType", "entity_type"]) ?? "").lowercased()
        let template = (text(row, ["entityTemplate", "entity_template"]) ?? "").lowercased()
        if let children = (row["entities"] ?? row["entityList"] ?? row["children"]) as? [Any], !children.isEmpty,
           (type.isEmpty && template.isEmpty || type.contains("card") || template.contains("card")) {
            return collectRows(children)
        }
        if type.isEmpty && template.isEmpty {
            for key in ["data", "list", "items", "rows", "result", "feeds", "cards"] {
                if let children = row[key] { return collectRows(children) }
            }
        }
        return [row]
    }

    private static func isSponsor(_ row: [String: Any]) -> Bool {
        let type = (text(row, ["entityType", "entity_type"]) ?? "").lowercased()
        let template = (text(row, ["entityTemplate", "entity_template"]) ?? "").lowercased()
        let sponsor = row["sponsorType"] ?? row["sponsor_type"]
        return type.hasPrefix("sponsor") || template.hasPrefix("sponsor")
            || sponsor.map { value in
                if let string = value as? String { return !string.trimmingCharacters(in: .whitespaces).isEmpty }
                if let number = value as? NSNumber { return number.doubleValue != 0 }
                if let array = value as? [Any] { return !array.isEmpty }
                if let object = value as? [String: Any] { return !object.isEmpty }
                return false
            } == true
    }

    private static func pageFields(_ root: [String: Any], _ payload: Any?) -> [[String: Any]] {
        let data = payload as? [String: Any] ?? [:]
        return [root, root["pagination"], root["pageInfo"], root["page_info"], data,
                data["pagination"], data["pageInfo"], data["page_info"]].compactMap { $0 as? [String: Any] }
    }

    private static func object(_ raw: String) -> [String: Any] {
        guard let data = raw.data(using: .utf8),
              let result = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [:] }
        return result
    }

    private static func text(_ row: [String: Any], _ keys: [String]) -> String? {
        keys.lazy.compactMap { key -> String? in
            guard let value = row[key], !(value is NSNull) else { return nil }
            if let string = value as? String { return string.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty }
            if let number = value as? NSNumber { return number.stringValue }
            return nil
        }.first
    }

    private static func bool(_ row: [String: Any], _ keys: [String]) -> Bool? {
        switch text(row, keys)?.lowercased() {
        case "true", "1": true
        case "false", "0": false
        default: nil
        }
    }

    private static func imageURL(_ raw: String?) -> URL? {
        guard let raw, !raw.isEmpty else { return nil }
        if raw.hasPrefix("http://") { return URL(string: "https://" + raw.dropFirst(7)) }
        if raw.hasPrefix("https://") { return URL(string: raw) }
        if raw.hasPrefix("//") { return URL(string: "https:" + raw) }
        return URL(string: "https://image.coolapk.com/" + raw.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
