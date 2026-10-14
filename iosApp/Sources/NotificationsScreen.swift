import Foundation
import SwiftUI

private enum NotificationCategory: String, CaseIterable, Identifiable {
    case comment = "list"
    case mention = "atMeList"
    case commentMention = "atCommentMeList"
    case like = "feedLikeList"
    case follow = "contactsFollowList"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .comment: "评论回复"
        case .mention: "@ 提及"
        case .commentMention: "评论 @"
        case .like: "收到的赞"
        case .follow: "新关注"
        }
    }
}

private struct NotificationEntry: Identifiable {
    let id: String
    let actorName: String
    let avatarURL: URL?
    let message: String
    let preview: String?
    let timestamp: String?
    let feedID: String?
    let unreadCount: Int
}

private struct NotificationPage {
    let items: [NotificationEntry]
    let hasMore: Bool
}

private enum NotificationParseError: LocalizedError {
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .invalidResponse: "通知数据格式无效，请稍后重试"
        }
    }
}

private enum NotificationParser {
    static func page(_ raw: String, category: NotificationCategory) throws -> NotificationPage {
        guard let data = raw.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw NotificationParseError.invalidResponse
        }
        guard let payload = root["data"] else { return NotificationPage(items: [], hasMore: false) }
        let notificationRows = rows(payload)
        let items = notificationRows.enumerated().map { index, row in entry(row, category: category, index: index) }
        let pageInfo = (root["pagination"] ?? root["pageInfo"]
            ?? (payload as? [String: Any])?["pagination"]) as? [String: Any]
        let more = value(root, ["hasMore", "has_more"])
            ?? pageInfo.flatMap { value($0, ["hasMore", "has_more"]) }
        return NotificationPage(
            items: items,
            hasMore: more.flatMap(boolean) ?? !notificationRows.isEmpty
        )
    }

    private static func entry(_ row: [String: Any], category: NotificationCategory, index: Int) -> NotificationEntry {
        let actor: [String: Any]
        if category == .like {
            actor = row["likeUserInfo"] as? [String: Any] ?? [:]
        } else {
            actor = row["fromUserInfo"] as? [String: Any]
                ?? row["userInfo"] as? [String: Any] ?? [:]
        }
        let actorName: String
        let avatar: String?
        if category == .like {
            actorName = value(row, ["likeUsername"]) ?? value(actor, ["username"]) ?? "酷友"
            avatar = value(row, ["likeAvatar"]) ?? value(actor, ["userAvatar", "avatar"])
        } else {
            actorName = value(row, ["fromusername", "username"])
                ?? value(actor, ["username", "name"]) ?? "酷友"
            avatar = value(row, ["fromUserAvatar", "userAvatar"])
                ?? value(actor, ["userAvatar", "avatar"])
        }

        let feedInfo = row["feedInfo"] as? [String: Any]
        let targetFeed = row["targetFeed"] as? [String: Any]
        let target = row["targetRow"] as? [String: Any]
        let firstReply = (row["replyRows"] as? [[String: Any]])?.first
        let message = value(row, ["note", "message", "message_title"])
            ?? firstReply.flatMap { value($0, ["message"]) }
            ?? fallbackMessage(category, row: row, feedInfo: feedInfo, target: target)
        let preview = value(row, ["targetTitle"])
            ?? feedInfo.flatMap { value($0, ["message_title", "title", "message"]) }
            ?? target.flatMap { value($0, ["message_title", "title", "message"]) }
        let id = value(row, ["id", "entityId", "entity_id"])
            ?? "\(category.rawValue):\(actorName):\(value(row, ["dateline", "likeTime"]) ?? ""):\(index)"
        let unread = Int(value(row, ["unread_count", "unreadCount"]) ?? "")
            ?? (["1", "true"].contains(value(row, ["isnew", "isNew"])?.lowercased() ?? "") ? 1 : 0)

        return NotificationEntry(
            id: id,
            actorName: actorName,
            avatarURL: FeedParser.imageURL(avatar),
            message: message,
            preview: preview,
            timestamp: value(row, ["dateline", "likeTime", "lastUpdate"]),
            feedID: relatedFeedID(row, feedInfo: feedInfo, targetFeed: targetFeed, target: target),
            unreadCount: max(0, unread)
        )
    }

    private static func fallbackMessage(
        _ category: NotificationCategory,
        row: [String: Any],
        feedInfo: [String: Any]?,
        target: [String: Any]?
    ) -> String {
        switch category {
        case .like: "赞了你的\(value(row, ["feedTypeName"]) ?? "动态")"
        case .follow: "关注了你"
        case .mention, .commentMention:
            feedInfo.flatMap { value($0, ["message"]) }
                ?? target.flatMap { value($0, ["message"]) } ?? "提到了你"
        case .comment:
            feedInfo.flatMap { value($0, ["message"]) }
                ?? target.flatMap { value($0, ["message"]) } ?? "回复了你"
        }
    }

    private static func relatedFeedID(
        _ row: [String: Any],
        feedInfo: [String: Any]?,
        targetFeed: [String: Any]?,
        target: [String: Any]?
    ) -> String? {
        let sources: [([String: Any], Bool)] = [
            (row, false),
            (feedInfo ?? [:], feedInfo != nil),
            (targetFeed ?? [:], targetFeed != nil),
            (target ?? [:], target.map(isFeed) ?? false),
        ]
        for (source, allowEntityID) in sources {
            for key in ["note", "message", "infoHtml", "url", "targetUrl"] {
                if let text = value(source, [key]), let id = feedLink(in: text) { return id }
            }
            if let id = value(source, ["feedId", "feed_id", "fid"]), isNumeric(id) { return id }
            if allowEntityID, let id = value(source, ["id", "entityId", "targetId"]), isNumeric(id) {
                return id
            }
        }
        return nil
    }

    private static func isFeed(_ row: [String: Any]) -> Bool {
        let type = value(row, ["entityType", "entity_type", "targetType", "feedType"]) ?? ""
        return type.localizedCaseInsensitiveContains("feed")
            || value(row, ["feedId", "feed_id", "fid", "message", "message_title"]) != nil
    }

    private static func feedLink(in text: String) -> String? {
        guard let range = text.range(of: "(?:^|[/?#])feed/[0-9]+", options: .regularExpression) else {
            return nil
        }
        return text[range].split(separator: "/").last.map(String.init)
    }

    private static func isNumeric(_ text: String) -> Bool {
        !text.isEmpty && text.allSatisfy(\.isNumber)
    }

    private static func rows(_ input: Any?) -> [[String: Any]] {
        if let array = input as? [Any] { return array.flatMap(rows) }
        guard let row = input as? [String: Any] else { return [] }
        if value(row, ["id", "entityId", "entity_id", "note", "message"]) == nil,
           let nested = row["data"] ?? row["list"] ?? row["entities"] ?? row["rows"] {
            return rows(nested)
        }
        return [row]
    }

    private static func boolean(_ text: String) -> Bool? {
        switch text.lowercased() {
        case "true", "1": true
        case "false", "0": false
        default: nil
        }
    }

    private static func value(_ row: [String: Any], _ keys: [String]) -> String? {
        for key in keys {
            guard let raw = row[key], !(raw is NSNull) else { continue }
            let text: String
            if let string = raw as? String {
                text = string
            } else if let number = raw as? NSNumber {
                text = number.stringValue
            } else {
                continue
            }
            if !text.isEmpty { return text }
        }
        return nil
    }
}

struct NotificationsScreen: View {
    let service: CoolapkService
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var category: NotificationCategory = .comment
    @State private var items: [NotificationEntry] = []
    @State private var loggedIn: Bool?
    @State private var page = 0
    @State private var hasMore = true
    @State private var loading = true
    @State private var error: String?
    @State private var requestID = UUID()
    @State private var selectedFeed: Feed?
    @State private var openingFeedID: String?
    @State private var feedOpenError: String?
    @State private var failedFeedID: String?

    var body: some View {
        VStack(spacing: 0) {
            categoryTabs
            Group {
                if loggedIn == nil && loading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if loggedIn == false {
                    status("登录后查看通知", symbol: "lock", detail: "请返回“我”导入 Cookie", action: "返回") {
                        dismiss()
                    }
                } else if items.isEmpty && loading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if items.isEmpty {
                    status(
                        error == nil ? "暂无\(category.title)通知" : "通知暂时无法加载",
                        symbol: error == nil ? "bell" : "wifi.exclamationmark",
                        detail: error,
                        action: error == nil ? "刷新" : "重试"
                    ) { Task { await load(reset: true) } }
                } else {
                    notificationList
                }
            }
        }
        .navigationTitle("消息")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { Task { await load(reset: true) } } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .accessibilityLabel("刷新通知")
                .disabled(loggedIn != true || loading)
            }
        }
        .task(id: category) { await openCategory() }
        .sheet(item: $selectedFeed) { feed in
            FeedDetailSheet(feed: feed, service: service)
        }
        .alert("动态加载失败", isPresented: Binding(
            get: { feedOpenError != nil },
            set: { if !$0 { feedOpenError = nil } }
        )) {
            Button("重试") {
                if let id = failedFeedID { Task { await openFeed(id) } }
            }
            Button("酷安网页") {
                if let id = failedFeedID,
                   let url = URL(string: "https://www.coolapk.com/feed/\(id)") {
                    openURL(url)
                }
            }
            Button("取消", role: .cancel) { feedOpenError = nil }
        } message: {
            Text(feedOpenError ?? "")
        }
    }

    private var categoryTabs: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(NotificationCategory.allCases) { option in
                    Button(option.title) { category = option }
                        .font(.subheadline.weight(category == option ? .semibold : .regular))
                        .foregroundStyle(category == option ? .white : .primary)
                        .padding(.horizontal, 14)
                        .frame(height: 36)
                        .background(
                            category == option ? Color(red: 0.02, green: 0.52, blue: 0.29)
                                : Color(uiColor: .secondarySystemBackground),
                            in: RoundedRectangle(cornerRadius: 8)
                        )
                        .accessibilityAddTraits(category == option ? .isSelected : [])
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
    }

    private var notificationList: some View {
        List {
            ForEach(items) { item in
                if let feedID = item.feedID {
                    Button { Task { await openFeed(feedID) } } label: {
                        notificationRow(item, opening: openingFeedID == feedID)
                    }
                    .buttonStyle(.plain)
                    .disabled(openingFeedID != nil)
                    .onAppear { loadMoreIfNeeded(item) }
                } else {
                    notificationRow(item, opening: false)
                        .onAppear { loadMoreIfNeeded(item) }
                }
            }
            if loading {
                ProgressView().frame(maxWidth: .infinity)
            }
            if let error {
                Button(error + " · 点击重试") { Task { await load(reset: false) } }
                    .frame(maxWidth: .infinity)
            }
        }
        .listStyle(.plain)
        .refreshable { await load(reset: true) }
    }

    private func notificationRow(_ item: NotificationEntry, opening: Bool) -> some View {
        HStack(alignment: .top, spacing: 14) {
            RemoteImage(url: item.avatarURL, fallback: "person.fill")
                .frame(width: 44, height: 44)
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 7) {
                    Text(item.actorName).font(.subheadline.weight(.semibold)).lineLimit(1)
                    if item.unreadCount > 0 {
                        Circle().fill(Color(red: 0.02, green: 0.52, blue: 0.29))
                            .frame(width: 7, height: 7)
                            .accessibilityLabel("未读")
                    }
                    Spacer(minLength: 4)
                    if let time = item.timestamp {
                        Text(displayTime(time)).font(.caption).foregroundStyle(.secondary)
                    }
                }
                Text(plainText(item.message)).font(.subheadline).lineLimit(3)
                if let preview = item.preview, !plainText(preview).isEmpty {
                    Text(plainText(preview)).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                }
            }
            if opening {
                ProgressView().controlSize(.small)
            } else if item.feedID != nil {
                Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
            }
        }
        .padding(.vertical, 8)
        .frame(maxWidth: 700, alignment: .leading)
        .contentShape(Rectangle())
    }

    private func status(
        _ title: String,
        symbol: String,
        detail: String?,
        action: String,
        onAction: @escaping () -> Void
    ) -> some View {
        ContentUnavailableView {
            Label(title, systemImage: symbol)
        } description: {
            if let detail { Text(detail) }
        } actions: {
            Button(action, action: onAction).buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func openCategory() async {
        let token = UUID()
        requestID = token
        items = []
        page = 0
        hasMore = true
        loading = true
        error = nil
        loggedIn = nil
        let signedIn = await service.isLoggedIn()
        guard requestID == token, !Task.isCancelled else { return }
        loggedIn = signedIn
        if signedIn {
            await load(reset: true)
        } else {
            loading = false
            hasMore = false
        }
    }

    private func load(reset: Bool) async {
        guard loggedIn == true else { return }
        if !reset { guard !loading, hasMore, page > 0 else { return } }
        let token = UUID()
        requestID = token
        let selectedCategory = category
        let nextPage = reset ? 1 : page + 1
        loading = true
        error = nil
        do {
            let raw = try await service.notifications(selectedCategory.rawValue, page: nextPage)
            let result = try NotificationParser.page(raw, category: selectedCategory)
            guard requestID == token, category == selectedCategory else { return }
            var seen = Set(reset ? [] : items.map(\.id))
            let newItems = result.items.filter { seen.insert($0.id).inserted }
            items = reset ? newItems : items + newItems
            hasMore = result.hasMore && !newItems.isEmpty
            page = nextPage
        } catch {
            guard requestID == token, category == selectedCategory else { return }
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func loadMoreIfNeeded(_ item: NotificationEntry) {
        if item.id == items.last?.id { Task { await load(reset: false) } }
    }

    private func openFeed(_ id: String) async {
        guard openingFeedID == nil else { return }
        openingFeedID = id
        feedOpenError = nil
        do {
            selectedFeed = try FeedDetailParser.parse(await service.feedDetail(id), feedID: id)
        } catch {
            failedFeedID = id
            feedOpenError = error.localizedDescription
        }
        openingFeedID = nil
    }

    private func plainText(_ raw: String) -> String {
        raw.replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression)
            .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func displayTime(_ raw: String) -> String {
        guard let number = Double(raw) else { return raw }
        let date = Date(timeIntervalSince1970: number > 100_000_000_000 ? number / 1_000 : number)
        let seconds = max(0, Int(Date().timeIntervalSince(date)))
        if seconds < 60 { return "刚刚" }
        if seconds < 3_600 { return "\(seconds / 60)分钟前" }
        if seconds < 86_400 { return "\(seconds / 3_600)小时前" }
        if seconds < 604_800 { return "\(seconds / 86_400)天前" }
        return date.formatted(.dateTime.year().month().day())
    }
}
