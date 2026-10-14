import SwiftUI

struct RemoteImage: View {
    let url: URL?
    let fallback: String
    var contentMode: ContentMode = .fill
    @State private var loaded: UIImage?

    var body: some View {
        GeometryReader { geometry in
            Group {
                if let loaded {
                    Image(uiImage: loaded).resizable().aspectRatio(contentMode: contentMode)
                } else {
                    Image(systemName: fallback).resizable().scaledToFit()
                        .padding(geometry.size.width * 0.24)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.green.opacity(0.08))
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
        }
        .task(id: url) {
            loaded = nil
            guard let url else { return }
            var request = URLRequest(url: url)
            if url.host?.lowercased() == "image.coolapk.com" {
                request.setValue("https://www.coolapk.com/", forHTTPHeaderField: "Referer")
                request.setValue("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Safari/537.36", forHTTPHeaderField: "User-Agent")
            }
            guard let (data, _) = try? await URLSession.shared.data(for: request),
                  !Task.isCancelled else { return }
            loaded = UIImage(data: data)
        }
    }
}

struct FeedRow: View {
    let feed: Feed
    let onOpen: () -> Void
    let onLike: () -> Void
    let onReply: () -> Void
    @State private var imagePresentation: ImageViewerPresentation?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button(action: onOpen) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .top, spacing: 10) {
                        RemoteImage(url: feed.avatarURL, fallback: "person.fill")
                            .frame(width: 42, height: 42).clipShape(Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text(feed.username).font(.subheadline.weight(.semibold))
                            HStack(spacing: 6) {
                                if let time = feed.timestamp { Text(time.displayTime) }
                                if let device = feed.device { Text(device).lineLimit(1) }
                            }
                            .font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 4)
                    }
                    if let title = feed.title, !title.isEmpty {
                        Text(title).font(.headline).foregroundStyle(.primary)
                    }
                    if !feed.message.isEmpty {
                        Text(feed.message.plainText).font(.body).foregroundStyle(.primary)
                            .lineLimit(5).multilineTextAlignment(.leading)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if !feed.imageURLs.isEmpty {
                HStack(spacing: 6) {
                    ForEach(Array(feed.imageURLs.prefix(3).enumerated()), id: \.offset) { index, url in
                        Button {
                            imagePresentation = ImageViewerPresentation(urls: feed.imageURLs, initialIndex: index)
                        } label: {
                            RemoteImage(url: url, fallback: "photo")
                                .frame(maxWidth: .infinity).frame(height: 108)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("查看第 \(index + 1) 张图片")
                    }
                }
            }

            HStack(spacing: 26) {
                Button(action: onLike) {
                    Label(feed.likes.formatted(), systemImage: feed.isLiked ? "heart.fill" : "heart")
                        .foregroundStyle(feed.isLiked ? Color(red: 0.02, green: 0.52, blue: 0.29) : .secondary)
                }
                .accessibilityLabel(feed.isLiked ? "取消点赞" : "点赞")
                Button(action: onReply) {
                    Label(feed.replies.formatted(), systemImage: "bubble.right")
                }
                .accessibilityLabel("评论")
                ShareLink(item: URL(string: "https://www.coolapk.com/feed/\(feed.id)")!) {
                    Label("分享", systemImage: "square.and.arrow.up")
                }
            }
            .font(.subheadline)
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
        .frame(maxWidth: 700, alignment: .leading)
        .fullScreenCover(item: $imagePresentation) { presentation in
            ImageViewer(presentation: presentation)
        }
    }
}

struct AppRow: View {
    let item: AppEntry

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            RemoteImage(url: item.iconURL, fallback: "app.fill")
                .frame(width: 58, height: 58)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 5) {
                Text(item.name).font(.headline)
                if let summary = item.summary, !summary.isEmpty {
                    Text(summary.plainText).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                }
                HStack(spacing: 12) {
                    if let version = item.version { Text("v\(version)") }
                    if let score = item.score { Label(score, systemImage: "star.fill") }
                    if let downloads = item.downloads { Text(downloads) }
                }
                .font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 7)
        .frame(maxWidth: 700, alignment: .leading)
    }
}

struct FeedDetailSheet: View {
    let feed: Feed
    let service: CoolapkService
    var focusComments = false
    @Environment(\.dismiss) private var dismiss
    @State private var showingReply = false
    @State private var replyRefresh = 0
    @State private var imagePresentation: ImageViewerPresentation?
    @State private var detailFeed: Feed?
    @State private var detailLoading = false
    @State private var detailError: String?

    private var visibleFeed: Feed { detailFeed ?? feed }

    var body: some View {
        NavigationStack {
            ScrollViewReader { scrollProxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        HStack(spacing: 12) {
                            RemoteImage(url: visibleFeed.avatarURL, fallback: "person.fill")
                                .frame(width: 48, height: 48).clipShape(Circle())
                            VStack(alignment: .leading) {
                                Text(visibleFeed.username).font(.headline)
                                if let time = visibleFeed.timestamp { Text(time.displayTime).font(.caption).foregroundStyle(.secondary) }
                            }
                        }
                        if detailLoading { ProgressView().controlSize(.small) }
                        if let detailError {
                            HStack(alignment: .top, spacing: 8) {
                                Text(detailError).frame(maxWidth: .infinity, alignment: .leading)
                                Button { Task { await loadDetail() } } label: {
                                    Image(systemName: "arrow.clockwise")
                                }
                                .accessibilityLabel("重试加载完整动态")
                            }
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                        if let title = visibleFeed.title { Text(title).font(.title3.bold()) }
                        Text(visibleFeed.message.plainText).font(.body)
                        ForEach(Array(visibleFeed.imageURLs.enumerated()), id: \.offset) { index, url in
                            Button {
                                imagePresentation = ImageViewerPresentation(urls: visibleFeed.imageURLs, initialIndex: index)
                            } label: {
                                RemoteImage(url: url, fallback: "photo")
                                    .frame(height: 240)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("查看第 \(index + 1) 张图片")
                        }
                        HStack {
                            Button { showingReply = true } label: { Label("发表评论", systemImage: "bubble.right") }
                            Spacer()
                            Link(destination: URL(string: "https://www.coolapk.com/feed/\(visibleFeed.id)")!) {
                                Label("酷安网页", systemImage: "arrow.up.right")
                            }
                        }
                        Divider()
                        FeedRepliesSection(feed: visibleFeed, service: service, refresh: replyRefresh)
                            .id("comments")
                    }
                    .frame(maxWidth: 700, alignment: .leading)
                    .padding(20)
                    .frame(maxWidth: .infinity)
                }
                .task {
                    if focusComments {
                        await Task.yield()
                        scrollProxy.scrollTo("comments", anchor: .top)
                    }
                    await loadDetail()
                    if focusComments {
                        await Task.yield()
                        scrollProxy.scrollTo("comments", anchor: .top)
                    }
                }
            }
            .navigationTitle("动态")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
            .sheet(isPresented: $showingReply) {
                ReplySheet(feed: visibleFeed, service: service, onSubmitted: { replyRefresh += 1 })
            }
            .fullScreenCover(item: $imagePresentation) { presentation in
                ImageViewer(presentation: presentation)
            }
        }
    }

    @MainActor private func loadDetail() async {
        guard !detailLoading else { return }
        detailLoading = true
        detailError = nil
        do {
            detailFeed = try FeedDetailParser.parse(await service.feedDetail(feed.id), feedID: feed.id)
        } catch {
            let reason = error.localizedDescription.lowercased()
            detailError = reason.contains("403") || reason.contains("captcha") || reason.contains("验证码")
                ? "服务端要求验证，当前显示列表内容；评论仍可查看"
                : "完整动态暂不可用，当前显示列表内容；评论仍可查看"
        }
        detailLoading = false
    }
}

struct ReplySheet: View {
    let feed: Feed
    let service: CoolapkService
    var onSubmitted: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss
    @State private var message = ""
    @State private var sending = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("回复 \(feed.username)") {
                    TextEditor(text: $message).frame(minHeight: 150)
                        .accessibilityLabel("评论内容")
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("发表评论")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("发送") { Task { await send() } }
                        .disabled(message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || sending)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func send() async {
        sending = true
        do {
            try await service.reply(message.trimmingCharacters(in: .whitespacesAndNewlines), feedID: feed.id)
            onSubmitted?()
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
        sending = false
    }
}

extension String {
    var plainText: String {
        replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression)
            .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var displayTime: String {
        guard let raw = Double(self) else { return self }
        let date = Date(timeIntervalSince1970: raw > 100_000_000_000 ? raw / 1000 : raw)
        let seconds = Int(Date().timeIntervalSince(date))
        if seconds < 60 { return "刚刚" }
        if seconds < 3600 { return "\(seconds / 60)分钟前" }
        if seconds < 86_400 { return "\(seconds / 3600)小时前" }
        if seconds < 604_800 { return "\(seconds / 86_400)天前" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
}
