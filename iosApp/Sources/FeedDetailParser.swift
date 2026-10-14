import Foundation

enum FeedDetailParseError: LocalizedError {
    case missingFeed

    var errorDescription: String? { "动态详情数据不完整，请稍后重试" }
}

enum FeedDetailParser {
    static func parse(_ raw: String, feedID: String) throws -> Feed {
        if let feed = FeedParser.page(raw).items.first(where: { $0.id == feedID }) {
            return feed
        }

        guard let entity = detailEntity(FeedParser.object(raw), feedID: feedID),
              let wrapped = try? JSONSerialization.data(withJSONObject: ["data": entity]),
              let text = String(data: wrapped, encoding: .utf8),
              let feed = FeedParser.page(text).items.first(where: { $0.id == feedID }) else {
            throw FeedDetailParseError.missingFeed
        }
        return feed
    }

    private static func detailEntity(_ value: Any?, feedID: String) -> [String: Any]? {
        if let rows = value as? [Any] {
            return rows.lazy.compactMap { detailEntity($0, feedID: feedID) }.first
        }
        guard let row = value as? [String: Any] else { return nil }
        let ids = ["id", "entityId", "entity_id"].compactMap { scalar(row[$0]) }
        let type = scalar(row["entityType"])?.lowercased() ?? ""
        let isFeed = row["message"] != nil || row["messageRaw"] != nil
            || ["feed", "article", "news", "question", "answer"].contains(type)
            || type.hasPrefix("feed_")
        if ids.contains(feedID), isFeed { return row }
        for key in ["data", "detail", "feed", "entity", "row"] {
            if let found = detailEntity(row[key], feedID: feedID) { return found }
        }
        return nil
    }

    private static func scalar(_ value: Any?) -> String? {
        if let text = value as? String { return text }
        if let number = value as? NSNumber { return number.stringValue }
        return nil
    }
}
