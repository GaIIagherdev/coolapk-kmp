import Foundation

struct AppDetail: Sendable {
    let name: String
    let packageName: String?
    let iconURL: URL?
    let developer: String?
    let version: String?
    let size: String?
    let updatedAt: String?
    let score: String?
    let downloads: String?
    let overview: String?
    let changeLog: String?
    let screenshotURLs: [URL]
}

enum AppDetailParser {
    static func parse(_ raw: String) -> AppDetail? {
        guard let root = CoreJSON.object(raw) else { return nil }
        let data = root["data"] as? [String: Any] ?? root
        guard let name = CoreJSON.text(data, ["title", "appName", "apkName", "name"]) else { return nil }
        let screenshots = CoreJSON.firstValue(data, ["screenList", "screenshots", "screenArr", "screenshot", "screen"])
        return AppDetail(
            name: name,
            packageName: CoreJSON.text(data, ["packageName", "apkPackageName", "package_name"]),
            iconURL: CoreJSON.imageURL(CoreJSON.text(data, ["apkRomIcon", "logo", "icon", "apkLogo"])),
            developer: CoreJSON.text(data, ["developername", "developerName", "developer"]),
            version: CoreJSON.text(data, ["apkversionname", "versionName", "version"]),
            size: CoreJSON.size(CoreJSON.text(data, ["apkSizeFormatted", "apksize", "size"])),
            updatedAt: CoreJSON.text(data, ["lastupdate", "lastUpdateFormatted", "update_time"]),
            score: CoreJSON.text(data, ["score", "rating"]),
            downloads: CoreJSON.text(data, ["downCountFormatted", "downCount", "downloadCount", "down_count"]),
            overview: CoreJSON.text(data, ["introduce", "description", "apkDescription", "intro"]),
            changeLog: CoreJSON.text(data, ["changeLog", "changelog"]),
            screenshotURLs: CoreJSON.imageURLs(screenshots)
        )
    }
}

enum NotificationCountParser {
    static func parse(_ raw: String) -> Int {
        guard let root = CoreJSON.object(raw) else { return 0 }
        if let count = CoreJSON.integer(root["data"]) { return max(0, count) }
        let data = root["data"] as? [String: Any] ?? root
        let keys = ["badge_v18", "badge", "count", "fcount", "total", "totalCount", "notificationCount", "unreadCount"]
        if let count = CoreJSON.integer(CoreJSON.firstValue(data, keys)) { return max(0, count) }
        return [["commentme", "commentMe", "comment"], ["atme", "atMe"],
                ["atcommentme", "atCommentMe"], ["feedlike", "feedLike"],
                ["contacts_follow", "contactsFollow", "follow"], ["message", "messageCount"]]
            .compactMap { CoreJSON.integer(CoreJSON.firstValue(data, $0)) }.reduce(0, +)
    }
}

private enum CoreJSON {
    static func object(_ raw: String) -> [String: Any]? {
        guard let data = raw.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    }

    static func firstValue(_ object: [String: Any], _ keys: [String]) -> Any? {
        keys.lazy.compactMap { object[$0] }.first
    }

    static func text(_ object: [String: Any], _ keys: [String]) -> String? {
        keys.lazy.compactMap { key -> String? in
            guard let value = object[key], !(value is NSNull) else { return nil }
            let text: String
            if let string = value as? String { text = string }
            else if let number = value as? NSNumber { text = number.stringValue }
            else { return nil }
            return text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || text == "null" ? nil : text
        }.first
    }

    static func integer(_ value: Any?) -> Int? {
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String { return Int(string) }
        return nil
    }

    static func size(_ raw: String?) -> String? {
        guard let raw else { return nil }
        guard let bytes = Int64(raw), bytes >= 0 else { return raw }
        return ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
    }

    static func imageURLs(_ value: Any?) -> [URL] {
        let urls: [URL]
        if let rows = value as? [Any] {
            urls = rows.flatMap(imageURLs)
        } else if let row = value as? [String: Any] {
            urls = imageURL(text(row, ["sourceUrl", "source_url", "url", "pic", "imageUrl", "src"])).map { [$0] } ?? []
        } else if let raw = value as? String {
            urls = raw.split(separator: ",").compactMap { imageURL(String($0)) }
        } else {
            urls = []
        }
        var seen = Set<URL>()
        return urls.filter { seen.insert($0).inserted }
    }

    static func imageURL(_ raw: String?) -> URL? {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !raw.isEmpty, raw != "null", raw != "undefined" else { return nil }
        if raw.hasPrefix("http://") { return URL(string: "https://" + raw.dropFirst(7)) }
        if raw.hasPrefix("https://") { return URL(string: raw) }
        if raw.hasPrefix("//") { return URL(string: "https:" + raw) }
        return URL(string: "https://image.coolapk.com/" + raw.trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }
}
