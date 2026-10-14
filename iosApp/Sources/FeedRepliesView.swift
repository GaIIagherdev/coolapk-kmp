import SwiftUI

private enum ReplyOrder: String, CaseIterable, Identifiable {
    case latest = "lastupdate_desc"
    case newest = "dateline_desc"
    case popular = "popular"

    var id: String { rawValue }
    var title: String {
        switch self {
        case .latest: "最近回复"
        case .newest: "最新发布"
        case .popular: "最热"
        }
    }
}

struct FeedRepliesSection: View {
    let feed: Feed
    let service: CoolapkService
    var refresh = 0
    @State private var order: ReplyOrder = .latest
    @State private var authorOnly = false
    @State private var items: [FeedReply] = []
    @State private var page = 0
    @State private var hasMore = true
    @State private var loading = false
    @State private var error: String?
    @State private var selectedReply: FeedReply?

    private var queryKey: String { "\(feed.id):\(order.rawValue):\(authorOnly):\(refresh)" }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("评论 \(feed.replies)").font(.title3.weight(.semibold))
            HStack {
                Picker("排序", selection: $order) {
                    ForEach(ReplyOrder.allCases) { item in Text(item.title).tag(item) }
                }
                .pickerStyle(.menu)
                Spacer()
                Toggle("只看楼主", isOn: $authorOnly)
                    .fixedSize()
            }
            .font(.subheadline)

            if (loading || (page == 0 && error == nil)) && items.isEmpty {
                ProgressView().frame(maxWidth: .infinity).padding(.vertical, 24)
            } else if items.isEmpty && error == nil {
                Text("暂无评论").foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity).padding(.vertical, 24)
            }

            ForEach(items) { reply in
                Divider()
                FeedReplyRow(reply: reply) { selectedReply = reply }
            }

            if let error {
                VStack(alignment: .leading, spacing: 8) {
                    Text(error).foregroundStyle(.secondary)
                    Button("重试") { Task { await loadMore() } }
                        .frame(minHeight: 44)
                }
                .font(.subheadline)
            } else if hasMore && !items.isEmpty {
                Button { Task { await loadMore() } } label: {
                    if loading { ProgressView() }
                    else { Text("加载更多评论") }
                }
                .disabled(loading)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 44)
            }
        }
        .task(id: queryKey) { await reload() }
        .sheet(item: $selectedReply) { reply in
            ReplyThreadSheet(feedID: feed.id, parent: reply, service: service)
        }
    }

    @MainActor private func reload() async {
        items = []
        page = 0
        hasMore = true
        loading = false
        error = nil
        await loadMore()
    }

    @MainActor private func loadMore() async {
        guard !loading, hasMore else { return }
        let requestedKey = queryKey
        loading = true
        error = nil
        do {
            let raw = try await service.feedReplies(
                feedID: feed.id, page: page + 1, listType: order.rawValue,
                fromFeedAuthor: authorOnly
            )
            guard !Task.isCancelled, requestedKey == queryKey else { return }
            let result = FeedReplyParser.page(raw)
            let existing = Set(items.map(\.id))
            items += result.items.filter { !existing.contains($0.id) }
            page += 1
            hasMore = result.hasMore && !result.items.isEmpty
        } catch {
            if !Task.isCancelled, requestedKey == queryKey { self.error = error.localizedDescription }
        }
        if requestedKey == queryKey { loading = false }
    }
}

private struct FeedReplyRow: View {
    let reply: FeedReply
    let onOpenThread: (() -> Void)?
    @State private var imagePresentation: ImageViewerPresentation?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                RemoteImage(url: reply.avatarURL, fallback: "person.fill")
                    .frame(width: 38, height: 38).clipShape(Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(reply.username).font(.subheadline.weight(.semibold))
                    HStack(spacing: 6) {
                        if let timestamp = reply.timestamp { Text(timestamp.displayTime) }
                        if let floor = reply.floor { Text("#\(floor)") }
                    }
                    .font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                if reply.likes > 0 {
                    Label(reply.likes.formatted(), systemImage: "heart")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            if let username = reply.replyToUsername {
                Text("回复 @\(username)").font(.caption).foregroundStyle(CoolapkStyle.green)
            }
            if !reply.message.isEmpty { Text(reply.message.plainText).font(.body) }
            if !reply.imageURLs.isEmpty {
                ScrollView(.horizontal) {
                    HStack(spacing: 8) {
                        ForEach(Array(reply.imageURLs.enumerated()), id: \.offset) { index, url in
                            Button {
                                imagePresentation = ImageViewerPresentation(urls: reply.imageURLs, initialIndex: index)
                            } label: {
                                RemoteImage(url: url, fallback: "photo")
                                    .frame(width: 140, height: 140)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("查看第 \(index + 1) 张评论图片")
                        }
                    }
                }
            }
            if !reply.previews.isEmpty {
                VStack(alignment: .leading, spacing: 5) {
                    ForEach(Array(reply.previews.prefix(2).enumerated()), id: \.offset) { _, preview in
                        Text("\(preview.username)：\(preview.message.plainText)")
                            .font(.subheadline).lineLimit(2)
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(CoolapkStyle.green.opacity(0.07), in: RoundedRectangle(cornerRadius: 8))
            }
            if reply.replyCount > 0, let onOpenThread {
                Button("查看 \(reply.replyCount) 条回复", action: onOpenThread)
                    .font(.subheadline.weight(.medium))
                    .frame(minHeight: 44)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 4)
        .fullScreenCover(item: $imagePresentation) { presentation in
            ImageViewer(presentation: presentation)
        }
    }
}

private struct ReplyThreadSheet: View {
    let feedID: String
    let parent: FeedReply
    let service: CoolapkService
    @Environment(\.dismiss) private var dismiss
    @State private var items: [FeedReply] = []
    @State private var page = 0
    @State private var lastItem = ""
    @State private var hasMore = true
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    FeedReplyRow(reply: parent, onOpenThread: nil)
                    ForEach(items) { reply in
                        Divider()
                        FeedReplyRow(reply: reply, onOpenThread: nil)
                    }
                    if loading || (page == 0 && error == nil) {
                        ProgressView().frame(maxWidth: .infinity).padding()
                    }
                    if let error {
                        Text(error).foregroundStyle(.secondary)
                        Button("重试") { Task { await loadMore() } }.frame(minHeight: 44)
                    } else if hasMore && !items.isEmpty && !loading {
                        Button("加载更多回复") { Task { await loadMore() } }
                            .frame(maxWidth: .infinity).frame(minHeight: 44)
                    } else if items.isEmpty && !loading && page > 0 {
                        Text("暂无更多回复").foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity).padding()
                    }
                }
                .frame(maxWidth: 700, alignment: .leading)
                .padding(20)
                .frame(maxWidth: .infinity)
            }
            .navigationTitle("楼中楼")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
            .task { if page == 0 { await loadMore() } }
        }
    }

    @MainActor private func loadMore() async {
        guard !loading, hasMore else { return }
        loading = true
        error = nil
        do {
            let raw = try await service.subReplies(
                feedID: feedID, replyID: parent.id, page: page + 1, lastItem: lastItem
            )
            let result = FeedReplyParser.page(raw)
            let existing = Set(items.map(\.id))
            items += result.items.filter { !existing.contains($0.id) }
            page += 1
            lastItem = result.lastItem ?? lastItem
            hasMore = result.hasMore && !result.items.isEmpty
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}
