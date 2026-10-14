import Foundation
import Security

struct Feed: Identifiable, Sendable {
    let id: String
    let title: String?
    let message: String
    let username: String
    let avatarURL: URL?
    let device: String?
    let timestamp: String?
    var likes: Int
    let replies: Int
    let imageURLs: [URL]
    var isLiked: Bool
}

struct FeedPage: Sendable {
    let items: [Feed]
    let firstItem: String?
    let lastItem: String?
    let hasMore: Bool
}

struct AppEntry: Identifiable, Sendable {
    let id: String
    let name: String
    let packageName: String?
    let iconURL: URL?
    let summary: String?
    let version: String?
    let score: String?
    let downloads: String?
}

struct AppPage: Sendable {
    let items: [AppEntry]
    let hasMore: Bool
}

struct DiscoveryResult: Sendable {
    let page: DiscoveryPage
    let feedsByID: [String: Feed]
}

struct SearchResponse: Sendable {
    let page: SearchPage
    let feedsByID: [String: Feed]
}

struct Profile: Sendable {
    let uid: String
    let username: String
    let avatarURL: URL?
    let bio: String?
    let feeds: Int
    let following: Int
    let followers: Int
}

enum FeedSource: String, CaseIterable, Identifiable {
    case home = "首页"
    case hot = "热榜"
    case discovery = "发现"

    var id: String { rawValue }
}

actor CoolapkService {
    private let core: CoolapkCore

    init() throws {
        core = try CoolapkCore()
        if let cookie = SessionKeychain.read() {
            core.setSessionCookie(cookie: cookie)
        }
    }

    func feed(_ source: FeedSource, page: Int, first: String? = nil, last: String? = nil) throws -> FeedPage {
        let page = UInt32(max(1, page))
        let raw: String
        switch source {
        case .home:
            raw = try core.getHomePage(page: page, firstItem: first ?? "", lastItem: last ?? "")
        case .hot:
            raw = try core.getHotFeeds(page: page)
        case .discovery:
            raw = try core.getLatestFeeds(page: page)
        }
        return FeedParser.page(raw)
    }

    func apps(page: Int, category: String) throws -> AppPage {
        let raw = try core.getAppList(page: UInt32(max(1, page)), category: category)
        return FeedParser.apps(raw)
    }

    func discoveryConfig() throws -> DiscoveryConfig {
        DiscoveryParser.config(try core.getDiscoveryConfig())
    }

    func discoveryPage(
        tab: DiscoveryTab,
        page: Int,
        firstItem: String?,
        lastItem: String?,
        pageContext: String?
    ) throws -> DiscoveryResult {
        let raw = try core.getDiscoveryPageData(
            url: tab.url,
            title: tab.title,
            subTitle: tab.subTitle ?? "",
            page: UInt32(max(1, page)),
            firstItem: firstItem ?? "",
            lastItem: lastItem ?? "",
            pageContext: pageContext ?? "",
            requestArgsJson: "{}"
        )
        return DiscoveryResult(
            page: DiscoveryParser.page(raw),
            feedsByID: FeedParser.page(raw).items.reduce(into: [:]) { result, feed in
                result[feed.id] = feed
            }
        )
    }

    func digitalConfig() throws -> DigitalConfig {
        DigitalParser.config(try core.getTabConfig())
    }

    func digitalPage(
        tab: DigitalTab,
        page: Int,
        firstItem: String?,
        lastItem: String?,
        pageContext: String?
    ) throws -> DigitalPage {
        DigitalParser.page(try core.getDiscoveryPageData(
            url: tab.url,
            title: tab.title,
            subTitle: tab.subTitle ?? "",
            page: UInt32(max(1, page)),
            firstItem: firstItem ?? "",
            lastItem: lastItem ?? "",
            pageContext: pageContext ?? "",
            requestArgsJson: tab.requestArgsJSON
        ))
    }

    func digitalCategoryProducts(
        category: DigitalEntity,
        page: Int,
        firstItem: String?,
        lastItem: String?
    ) throws -> DigitalPage {
        guard let url = category.url, !url.isEmpty else { throw ServiceError.invalidDigitalCategory }
        return DigitalParser.page(try core.getProductList(
            url: url,
            title: category.title ?? "",
            subTitle: category.subTitle ?? "",
            page: UInt32(max(1, page)),
            firstItem: firstItem ?? "",
            lastItem: lastItem ?? ""
        ))
    }

    func digitalBrands() throws -> [DigitalEntity] {
        DigitalParser.page(try core.getProductBrandList()).items
    }

    func digitalCategories() throws -> [DigitalEntity] {
        DigitalParser.page(try core.getProductCategoryList()).items
    }

    func digitalBrandProducts(
        brandID: String,
        brandType: String,
        page: Int,
        firstItem: String?,
        lastItem: String?
    ) throws -> DigitalPage {
        DigitalParser.page(try core.getProductBrandProducts(
            brandId: brandID,
            brandType: brandType,
            page: UInt32(max(1, page)),
            firstItem: firstItem ?? "",
            lastItem: lastItem ?? ""
        ))
    }

    func digitalProductDetail(_ productID: String) throws -> DigitalEntity? {
        let page = DigitalParser.page(try core.getProductDetail(productId: productID))
        return page.items.first
    }

    func appDetail(_ packageName: String) throws -> AppDetail {
        let raw = try core.getAppDetail(packageName: packageName)
        guard let detail = AppDetailParser.parse(raw) else { throw ServiceError.invalidAppDetail }
        return detail
    }

    func feedDetail(_ id: String) throws -> String {
        try core.getFeedDetail(feedId: id)
    }

    func feedReplies(feedID: String, page: Int, listType: String, fromFeedAuthor: Bool) throws -> String {
        try core.getFeedReplies(
            feedId: feedID,
            page: UInt32(max(1, page)),
            listType: listType,
            fromFeedAuthor: fromFeedAuthor
        )
    }

    func subReplies(feedID: String, replyID: String, page: Int, lastItem: String) throws -> String {
        try core.getSubReplies(
            feedId: feedID,
            replyId: replyID,
            page: UInt32(max(1, page)),
            lastItem: lastItem
        )
    }

    func notifications(_ type: String, page: Int) throws -> String {
        try core.getNotifications(notificationType: type, page: UInt32(max(1, page)))
    }

    func search(_ query: String, page: Int) throws -> FeedPage {
        FeedParser.page(try core.searchFeeds(query: query, page: UInt32(max(1, page))))
    }

    func searchByType(
        _ category: SearchCategory,
        query: String,
        page: Int,
        firstItem: String?,
        lastItem: String?
    ) throws -> SearchResponse {
        let raw = try core.searchByType(
            searchType: category.rawValue,
            query: query,
            page: UInt32(max(1, page)),
            firstItem: firstItem ?? "",
            lastItem: lastItem ?? "",
            feedType: ""
        )
        return SearchResponse(
            page: SearchParser.page(raw, category: category),
            feedsByID: FeedParser.page(raw).items.reduce(into: [:]) { result, feed in
                result[feed.id] = feed
            }
        )
    }

    func hotSearches(refresh: Bool) throws -> [String] {
        SearchParser.hotWords(try core.getHotSearches(refresh: refresh))
    }

    func setLiked(_ liked: Bool, feedID: String) throws {
        if liked {
            _ = try core.likeFeed(feedId: feedID)
        } else {
            _ = try core.unlikeFeed(feedId: feedID)
        }
    }

    func reply(_ message: String, feedID: String) throws {
        _ = try core.replyFeed(feedId: feedID, message: message)
    }

    func login(cookie: String) throws {
        let previousCookie = core.getSessionCookie()
        do {
            _ = try core.loginByWebviewCookie(cookie: cookie.trimmingCharacters(in: .whitespacesAndNewlines))
        } catch {
            core.setSessionCookie(cookie: previousCookie ?? "")
            throw error
        }
        try persistSession(previousCookie: previousCookie)
    }

    func login(accessCode: String?, cookie: String) throws {
        let normalizedCookie = cookie.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let accessCode, !accessCode.isEmpty else {
            try login(cookie: normalizedCookie)
            return
        }
        let previousCookie = core.getSessionCookie()
        do {
            _ = try core.loginByAccessCode(code: accessCode, cookie: normalizedCookie)
        } catch {
            core.setSessionCookie(cookie: previousCookie ?? "")
            try login(cookie: normalizedCookie)
            return
        }
        try persistSession(previousCookie: previousCookie)
    }

    private func persistSession(previousCookie: String?) throws {
        guard let verified = core.getSessionCookie(), core.isLoggedIn() else {
            core.setSessionCookie(cookie: previousCookie ?? "")
            throw ServiceError.invalidSession
        }
        do {
            try SessionKeychain.write(verified)
        } catch {
            core.setSessionCookie(cookie: previousCookie ?? "")
            throw error
        }
    }

    func logout() throws {
        try SessionKeychain.clear()
        core.setSessionCookie(cookie: "")
    }

    func isLoggedIn() -> Bool { core.isLoggedIn() }

    func accountKey() -> String {
        guard let cookie = core.getSessionCookie(), core.isLoggedIn() else { return "guest" }
        return "uid:" + (cookie.cookieField("uid") ?? "logged-in")
    }

    func createFeed(message: String) throws -> String {
        let value = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { throw ServiceError.emptyPublishMessage }
        return try core.createFeed(message: value)
    }

    func profile() throws -> Profile? {
        guard let uid = core.getSessionCookie()?.cookieField("uid"), !uid.isEmpty else { return nil }
        return FeedParser.profile(try core.getMyProfile(uid: uid))
    }

    func notificationCount() throws -> Int {
        NotificationCountParser.parse(try core.getNotificationCount())
    }
}

private enum ServiceError: LocalizedError {
    case invalidAppDetail
    case invalidDigitalCategory
    case invalidSession
    case emptyPublishMessage
    case secureStorage(OSStatus)

    var errorDescription: String? {
        switch self {
        case .invalidAppDetail: "应用详情数据不完整"
        case .invalidDigitalCategory: "数码分类缺少服务端地址"
        case .invalidSession: "登录凭据无效，请重新获取 Cookie"
        case .emptyPublishMessage: "请输入动态内容"
        case .secureStorage: "无法安全保存会话，请检查设备钥匙串状态"
        }
    }
}

private enum SessionKeychain {
    static let account = "coolapk-kmp-session"

    static func read() -> String? {
        var result: CFTypeRef?
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func write(_ cookie: String) throws {
        let identity: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
        ]
        let update: [String: Any] = [kSecValueData as String: Data(cookie.utf8)]
        let status = SecItemUpdate(identity as CFDictionary, update as CFDictionary)
        if status == errSecSuccess { return }
        guard status == errSecItemNotFound else { throw ServiceError.secureStorage(status) }
        let query: [String: Any] = identity.merging([
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: Data(cookie.utf8),
        ]) { _, new in new }
        let addStatus = SecItemAdd(query as CFDictionary, nil)
        guard addStatus == errSecSuccess else { throw ServiceError.secureStorage(addStatus) }
    }

    static func clear() throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
        ]
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw ServiceError.secureStorage(status)
        }
    }
}

private extension String {
    func cookieField(_ name: String) -> String? {
        split(separator: ";").map { $0.trimmingCharacters(in: .whitespaces) }
            .first { $0.lowercased().hasPrefix("\(name.lowercased())=") }
            .flatMap { $0.split(separator: "=", maxSplits: 1).last.map(String.init) }
    }
}

enum FeedParser {
    static func object(_ raw: String) -> [String: Any] {
        guard let data = raw.data(using: .utf8),
              let value = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [:] }
        return value
    }

    static func page(_ raw: String) -> FeedPage {
        let root = object(raw)
        let payload = root["data"]
        let objects = entities(payload, isMatch: isFeed)
        let cursors = entities(payload, isMatch: { string($0, ["entityId", "entity_id", "id"]) != nil })
        let fields = pageFields(root, payload)
        let first = fields.compactMap { string($0, ["firstItem", "first_item"]) }.first
            ?? cursors.first.flatMap { string($0, ["entityId", "entity_id", "id"]) }
        let last = fields.compactMap { string($0, ["lastItem", "last_item"]) }.first
            ?? cursors.last.flatMap { string($0, ["entityId", "entity_id", "id"]) }
        let more = fields.compactMap { boolean($0, ["hasMore", "has_more"]) }.first ?? !cursors.isEmpty
        return FeedPage(items: objects.compactMap(feed), firstItem: first, lastItem: last, hasMore: more)
    }

    static func apps(_ raw: String) -> AppPage {
        let root = object(raw)
        let payload = root["data"]
        let objects = entities(payload, isMatch: { item in
            string(item, ["packageName", "apkPackageName", "package_name", "apkName", "apkname"]) != nil
                || ["apk", "game"].contains(string(item, ["entityType"]) ?? "")
        })
        var seen = Set<String>()
        let items = objects.compactMap(app).filter { seen.insert($0.id).inserted }
        let more = pageFields(root, payload).compactMap { boolean($0, ["hasMore", "has_more"]) }.first
            ?? !objects.isEmpty
        return AppPage(items: items, hasMore: more)
    }

    static func profile(_ raw: String) -> Profile? {
        guard let data = object(raw)["data"] as? [String: Any],
              let uid = string(data, ["uid", "id"]) else { return nil }
        return Profile(
            uid: uid,
            username: string(data, ["username", "userName", "displayUserName", "displayUsername", "user_name"]) ?? "酷安用户",
            avatarURL: imageURL(string(data, ["userAvatar", "avatar", "user_avatar"])),
            bio: string(data, ["bio", "signature", "sign", "description"]),
            feeds: integer(data, ["feedNum", "feednum", "feedCount", "feed_num"]) ?? 0,
            following: integer(data, ["followNum", "follownum", "follow", "followCount", "follow_num"]) ?? 0,
            followers: integer(data, ["fansNum", "fansnum", "fans", "fansCount", "fans_num"]) ?? 0
        )
    }

    static func integer(_ object: [String: Any], _ keys: [String]) -> Int? {
        keys.lazy.compactMap { key in
            guard let value = object[key] else { return nil as Int? }
            return Int(String(describing: value))
        }.first
    }

    private static func string(_ object: [String: Any], _ keys: [String]) -> String? {
        keys.lazy.compactMap { key in
            guard let value = object[key], !(value is NSNull) else { return nil as String? }
            let text = String(describing: value)
            return text.isEmpty ? nil : text
        }.first
    }

    private static func boolean(_ object: [String: Any], _ keys: [String]) -> Bool? {
        guard let text = string(object, keys)?.lowercased() else { return nil }
        if text == "true" || text == "1" { return true }
        if text == "false" || text == "0" { return false }
        return nil
    }

    private static func pageFields(_ root: [String: Any], _ payload: Any?) -> [[String: Any]] {
        let data = payload as? [String: Any] ?? [:]
        return [root, root["pagination"], root["pageInfo"], root["page_info"],
                data, data["pagination"], data["pageInfo"], data["page_info"]]
            .compactMap { $0 as? [String: Any] }
    }

    private static func entities(_ value: Any?, isMatch: ([String: Any]) -> Bool) -> [[String: Any]] {
        if let array = value as? [Any] { return array.flatMap { entities($0, isMatch: isMatch) } }
        guard let object = value as? [String: Any] else { return [] }
        if isMatch(object) { return [object] }
        return ["data", "entities", "feeds", "list", "rows", "result"]
            .flatMap { entities(object[$0], isMatch: isMatch) }
    }

    private static func isFeed(_ item: [String: Any]) -> Bool {
        guard string(item, ["entityId", "id"]) != nil else { return false }
        if let type = string(item, ["entityType", "entity_type"])?.lowercased() {
            return ["feed", "feedreply", "article", "news", "dyh", "question", "answer"].contains(type)
                || type.hasPrefix("feed_")
        }
        return string(item, ["message", "messageRaw"]) != nil
            || (string(item, ["title"]) != nil && string(item, ["username"]) != nil)
    }

    private static func feed(_ item: [String: Any]) -> Feed? {
        guard let id = string(item, ["id", "entityId"]) else { return nil }
        let user = item["userInfo"] as? [String: Any] ?? item["user"] as? [String: Any] ?? [:]
        let imageRows = ["pics", "imageUriList", "picArr"].lazy.compactMap { item[$0] as? [Any] }.first
        let images = imageRows?.compactMap { value -> URL? in
            if let row = value as? [String: Any] {
                return imageURL(string(row, ["sourceUrl", "source_url", "inSource", "in_source", "url", "pic", "imageUrl", "image_url", "src"]))
            }
            return imageURL(value as? String)
        } ?? [imageURL(string(item, ["pic"]))].compactMap { $0 }
        let action = item["userAction"] as? [String: Any] ?? [:]
        return Feed(
            id: id,
            title: string(item, ["title"]),
            message: string(item, ["message", "messageRaw"]) ?? "",
            username: string(item, ["username", "userName"]) ?? string(user, ["username", "name"]) ?? "酷安用户",
            avatarURL: imageURL(string(item, ["userAvatar"]) ?? string(user, ["userAvatar"])),
            device: string(item, ["deviceTitle", "deviceName"]),
            timestamp: string(item, ["dateline"]),
            likes: integer(item, ["likenum", "likeCount"]) ?? 0,
            replies: integer(item, ["replynum", "replyCount"]) ?? 0,
            imageURLs: images,
            isLiked: boolean(action, ["like"]) ?? boolean(item, ["isLiked", "isLike", "is_liked"]) ?? false
        )
    }

    private static func app(_ item: [String: Any]) -> AppEntry? {
        let package = string(item, ["packageName", "apkPackageName", "package_name", "apkname"])
        guard let id = string(item, ["id", "entityId"]) ?? package,
              let name = string(item, ["appName", "apkName", "title", "name", "shorttitle"]) else { return nil }
        return AppEntry(
            id: id,
            name: name,
            packageName: package,
            iconURL: imageURL(string(item, ["icon", "logo", "apkRomIcon", "apkLogo"])),
            summary: string(item, ["description", "brief", "subTitle"]),
            version: string(item, ["versionName", "version"]),
            score: string(item, ["score", "rating"]),
            downloads: string(item, ["downCountFormatted", "downloadCount", "downnum", "download_num"])
        )
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
