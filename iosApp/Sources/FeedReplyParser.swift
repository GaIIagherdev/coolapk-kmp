import Foundation

struct FeedReply: Identifiable, Sendable {
    let id: String
    let username: String
    let avatarURL: URL?
    let replyToUsername: String?
    let message: String
    let timestamp: String?
    let floor: String?
    let likes: Int
    let replyCount: Int
    let imageURLs: [URL]
    let previews: [ReplyPreview]
}

struct ReplyPreview: Sendable {
    let username: String
    let message: String
}

struct FeedReplyPage: Sendable {
    let items: [FeedReply]
    let hasMore: Bool
    let lastItem: String?
}

enum FeedReplyParser {
    static func page(_ raw: String) -> FeedReplyPage {
        guard let data = raw.data(using: .utf8),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let payload = root["data"] else {
            return FeedReplyPage(items: [], hasMore: false, lastItem: nil)
        }
        let rows = replyRows(payload)
        var seen = Set<String>()
        let items = rows.compactMap(reply).filter { seen.insert($0.id).inserted }
        let nested = payload as? [String: Any] ?? [:]
        let hasMore = boolean(root, ["hasMore", "has_more"])
            ?? boolean(nested, ["hasMore", "has_more"])
            ?? !rows.isEmpty
        return FeedReplyPage(items: items, hasMore: hasMore, lastItem: items.last?.id)
    }

    private static func replyRows(_ value: Any) -> [[String: Any]] {
        if let array = value as? [Any] { return array.flatMap(replyRows) }
        guard let object = value as? [String: Any] else { return [] }
        if string(object, ["id", "entityId"]) != nil { return [object] }
        for key in ["list", "rows", "data", "entities"] {
            if let nested = object[key] { return replyRows(nested) }
        }
        return []
    }

    private static func reply(_ row: [String: Any]) -> FeedReply? {
        guard let id = string(row, ["id", "entityId"]) else { return nil }
        let user = row["userInfo"] as? [String: Any] ?? row["user"] as? [String: Any] ?? [:]
        let previews = (row["replyRows"] as? [[String: Any]] ?? []).compactMap { child -> ReplyPreview? in
            guard let message = string(child, ["message", "description"]) else { return nil }
            let childUser = child["userInfo"] as? [String: Any] ?? [:]
            return ReplyPreview(
                username: string(child, ["username"]) ?? string(childUser, ["username"]) ?? "酷友",
                message: message
            )
        }
        let pictures = (row["picArr"] as? [Any] ?? row["images"] as? [Any] ?? [])
            .compactMap { imageURL(imageString($0)) }
        let fallbackPicture = imageURL(string(row, ["pic"]))
        return FeedReply(
            id: id,
            username: string(row, ["username"]) ?? string(user, ["username"]) ?? "酷友",
            avatarURL: imageURL(string(row, ["userAvatar"]) ?? string(user, ["userAvatar"])),
            replyToUsername: string(row, ["replyUsername", "rusername"]),
            message: string(row, ["message", "description"]) ?? "",
            timestamp: string(row, ["dateline"]),
            floor: string(row, ["floor"]).flatMap { $0 == "0" ? nil : $0 },
            likes: integer(row, ["likenum"]) ?? 0,
            replyCount: max(integer(row, ["replyRowsCount"]) ?? 0,
                            integer(row, ["replynum"]) ?? 0, previews.count),
            imageURLs: pictures.isEmpty ? [fallbackPicture].compactMap { $0 } : pictures,
            previews: previews
        )
    }

    private static func imageString(_ value: Any) -> String? {
        if let object = value as? [String: Any] {
            return string(object, ["sourceUrl", "source_url", "url", "pic", "imageUrl"])
        }
        return value as? String
    }

    private static func imageURL(_ raw: String?) -> URL? {
        guard let value = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty, value != "null", value != "undefined" else { return nil }
        if value.hasPrefix("http://") { return URL(string: "https://" + value.dropFirst(7)) }
        if value.hasPrefix("https://") { return URL(string: value) }
        if value.hasPrefix("//") { return URL(string: "https:" + value) }
        return URL(string: "https://image.coolapk.com/" + value.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }

    private static func string(_ object: [String: Any], _ keys: [String]) -> String? {
        keys.lazy.compactMap { key -> String? in
            guard let value = object[key], !(value is NSNull) else { return nil }
            let result = String(describing: value)
            return result.isEmpty || result == "null" ? nil : result
        }.first
    }

    private static func integer(_ object: [String: Any], _ keys: [String]) -> Int? {
        string(object, keys).flatMap(Int.init)
    }

    private static func boolean(_ object: [String: Any], _ keys: [String]) -> Bool? {
        guard let value = string(object, keys)?.lowercased() else { return nil }
        if value == "true" || value == "1" { return true }
        if value == "false" || value == "0" { return false }
        return nil
    }
}
