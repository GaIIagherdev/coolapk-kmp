import SwiftUI

private struct DiscoveryAppRoute: Hashable {
    let packageName: String
    let title: String
}

private struct DiscoveryGroup: Identifiable {
    let id: String
    var cards: [DiscoveryCard]
    let compact: Bool
}

struct DiscoveryScreen: View {
    let service: CoolapkService

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.openURL) private var openURL
    @State private var tabs: [DiscoveryTab] = []
    @State private var selectedTab: DiscoveryTab?
    @State private var cards: [DiscoveryCard] = []
    @State private var feedsByID: [String: Feed] = [:]
    @State private var page = 0
    @State private var firstItem: String?
    @State private var lastItem: String?
    @State private var pageContext: String?
    @State private var hasMore = true
    @State private var configLoading = true
    @State private var loading = false
    @State private var error: String?
    @State private var fallbackReason: String?
    @State private var selectedFeed: Feed?
    @State private var focusComments = false
    @State private var requestID = UUID()

    var body: some View {
        Group {
            if tabs.isEmpty && configLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if tabs.isEmpty {
                fallbackContent
            } else {
                configuredContent
            }
        }
        .navigationTitle("发现")
        .navigationDestination(for: DiscoveryAppRoute.self) { route in
            AppDetailScreen(
                item: AppEntry(id: route.packageName, name: route.title,
                               packageName: route.packageName, iconURL: nil, summary: nil,
                               version: nil, score: nil, downloads: nil),
                service: service
            )
        }
        .sheet(item: $selectedFeed) {
            FeedDetailSheet(feed: $0, service: service, focusComments: focusComments)
        }
        .task { if tabs.isEmpty && fallbackReason == nil { await loadConfig() } }
    }

    private var fallbackContent: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Image(systemName: "wifi.exclamationmark")
                Text(fallbackReason ?? "栏目暂不可用，当前显示科技快讯")
                    .font(.caption)
                Spacer(minLength: 4)
                Button { Task { await loadConfig() } } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .accessibilityLabel("重试发现栏目")
            }
            .foregroundStyle(.secondary)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            TimelineScreen(service: service, source: .discovery)
        }
    }

    private var configuredContent: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(tabs, id: \.key) { tab in
                        let isSelected = selectedTab?.key == tab.key
                        Button { select(tab) } label: {
                            Text(tab.title)
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                                .padding(.horizontal, 14)
                                .frame(minHeight: 36)
                                .foregroundStyle(isSelected ? Color.white : Color.primary)
                                .background(isSelected ? accentColor : Color(uiColor: .secondarySystemBackground))
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
            }
            Divider()
            if cards.isEmpty && loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if cards.isEmpty {
                ContentUnavailableView {
                    Label(error == nil ? "暂无内容" : "栏目加载失败", systemImage: "safari")
                } description: {
                    Text(error ?? "下拉刷新后再试")
                } actions: {
                    Button("重试") { Task { await loadPage(reset: true) } }
                        .buttonStyle(.borderedProminent)
                }
                .frame(maxHeight: .infinity)
            } else {
                cardList
            }
        }
    }

    private var cardList: some View {
        List {
            ForEach(groups) { group in
                if group.compact {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        ForEach(group.cards) { card in
                            cardEntry(card, compact: true)
                        }
                    }
                    .listRowSeparator(.hidden)
                    .onAppear { if group.id == groups.last?.id { Task { await loadPage(reset: false) } } }
                } else if let card = group.cards.first {
                    Group {
                        if let feedID = card.feedID, let feed = feedsByID[feedID] {
                            FeedRow(
                                feed: feed,
                                onOpen: { openFeed(feed) },
                                onLike: { Task { await toggleLike(feed) } },
                                onReply: { openFeed(feed, focusComments: true) }
                            )
                        } else {
                            cardEntry(card, compact: false)
                        }
                    }
                    .listRowSeparator(.hidden)
                    .onAppear { if group.id == groups.last?.id { Task { await loadPage(reset: false) } } }
                }
            }
            if loading {
                ProgressView().frame(maxWidth: .infinity).listRowSeparator(.hidden)
            }
            if let error {
                Button("加载失败，点击重试") { Task { await loadPage(reset: page == 0) } }
                    .frame(maxWidth: .infinity)
                    .listRowSeparator(.hidden)
                    .accessibilityHint(error)
            }
        }
        .listStyle(.plain)
        .refreshable { await loadPage(reset: true) }
    }

    private var groups: [DiscoveryGroup] {
        var result: [DiscoveryGroup] = []
        for card in cards {
            let compact = card.entityType?.lowercased() == "selectorlink"
            if compact, result.last?.compact == true {
                result[result.count - 1].cards.append(card)
            } else {
                result.append(DiscoveryGroup(id: card.id, cards: [card], compact: compact))
            }
        }
        return result
    }

    private var accentColor: Color {
        colorScheme == .dark ? CoolapkStyle.darkGreen : CoolapkStyle.green
    }

    @ViewBuilder
    private func cardEntry(_ card: DiscoveryCard, compact: Bool) -> some View {
        if let package = pathValue("apk", in: card.url) {
            NavigationLink(value: DiscoveryAppRoute(packageName: package, title: card.title ?? "应用详情")) {
                cardContent(card, compact: compact)
            }
            .buttonStyle(.plain)
        } else if card.feedID != nil || pathValue("feed", in: card.url) != nil || browserURL(card.url) != nil {
            Button { openCard(card) } label: { cardContent(card, compact: compact) }
                .buttonStyle(.plain)
        } else {
            cardContent(card, compact: compact)
        }
    }

    private func cardContent(_ card: DiscoveryCard, compact: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if !compact, let imageURL = card.imageURL {
                RemoteImage(url: imageURL, fallback: "photo")
                    .frame(height: 180)
                    .clipped()
            }
            VStack(alignment: .leading, spacing: 6) {
                Text(card.title?.plainText ?? "查看内容")
                    .font(compact ? .subheadline.weight(.semibold) : .headline)
                    .lineLimit(compact ? 2 : 3)
                if !compact, let description = card.description?.plainText, !description.isEmpty {
                    Text(description).font(.subheadline).foregroundStyle(.secondary)
                        .lineLimit(4)
                }
            }
            .frame(maxWidth: .infinity, minHeight: compact ? 52 : 0, alignment: .leading)
            .padding(compact ? 12 : 16)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func select(_ tab: DiscoveryTab) {
        if let raw = tab.webURL, let webURL = URL(string: raw),
           ["http", "https"].contains(webURL.scheme?.lowercased() ?? "") {
            openURL(webURL)
            return
        }
        guard selectedTab?.key != tab.key else { return }
        selectedTab = tab
        cards = []
        feedsByID = [:]
        page = 0
        firstItem = nil
        lastItem = nil
        pageContext = nil
        hasMore = true
        error = nil
        Task { await loadPage(reset: true) }
    }

    private func openCard(_ card: DiscoveryCard) {
        if let feedID = card.feedID ?? pathValue("feed", in: card.url) {
            let feed = feedsByID[feedID] ?? Feed(
                id: feedID, title: card.title, message: card.description ?? "",
                username: "酷友", avatarURL: nil, device: nil, timestamp: nil,
                likes: 0, replies: 0, imageURLs: card.imageURL.map { [$0] } ?? [], isLiked: false
            )
            openFeed(feed)
        } else if let url = browserURL(card.url) {
            openURL(url)
        }
    }

    private func openFeed(_ feed: Feed, focusComments: Bool = false) {
        self.focusComments = focusComments
        selectedFeed = feed
    }

    private func browserURL(_ raw: String?) -> URL? {
        guard let raw else { return nil }
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") { return URL(string: raw) }
        if raw.hasPrefix("#/") { return URL(string: "https://www.coolapk.com/" + raw) }
        if raw.hasPrefix("/") && !raw.hasPrefix("//") {
            return URL(string: "https://www.coolapk.com" + raw)
        }
        return nil
    }

    private func pathValue(_ key: String, in raw: String?) -> String? {
        guard let raw else { return nil }
        let parts = raw.replacingOccurrences(of: "#/", with: "/").split(separator: "/")
        guard let index = parts.firstIndex(where: { $0.lowercased() == key }),
              parts.indices.contains(index + 1) else { return nil }
        let value = String(parts[index + 1].split(whereSeparator: { $0 == "?" || $0 == "#" }).first ?? "")
        if key == "feed" { return value.allSatisfy(\.isNumber) && !value.isEmpty ? value : nil }
        return value.allSatisfy { $0.isLetter || $0.isNumber || $0 == "." || $0 == "_" || $0 == "-" }
            && !value.isEmpty ? value : nil
    }

    private func loadConfig() async {
        let currentRequest = UUID()
        requestID = currentRequest
        configLoading = true
        do {
            let config = try await service.discoveryConfig()
            guard requestID == currentRequest, !Task.isCancelled else { return }
            guard !config.tabs.isEmpty else {
                fallbackReason = "栏目暂无可用数据，当前显示科技快讯"
                configLoading = false
                return
            }
            tabs = config.tabs
            selectedTab = config.tabs.first { $0.key == config.selectedKey } ?? config.tabs.first
            fallbackReason = nil
            configLoading = false
            await loadPage(reset: true)
        } catch {
            guard requestID == currentRequest, !Task.isCancelled else { return }
            fallbackReason = "栏目暂不可用，当前显示科技快讯"
            configLoading = false
        }
    }

    private func loadPage(reset: Bool) async {
        guard let tab = selectedTab else { return }
        if !reset { guard !loading, page > 0, hasMore else { return } }
        let currentRequest = UUID()
        requestID = currentRequest
        let nextPage = reset ? 1 : page + 1
        loading = true
        error = nil
        do {
            let result = try await service.discoveryPage(
                tab: tab, page: nextPage,
                firstItem: reset ? nil : firstItem,
                lastItem: reset ? nil : lastItem,
                pageContext: reset ? nil : pageContext
            )
            guard requestID == currentRequest, selectedTab?.key == tab.key, !Task.isCancelled else { return }
            let previousIDs = Set(cards.map(\.id))
            let incoming = result.page.cards
            if reset {
                cards = incoming
                feedsByID = result.feedsByID
            } else {
                var seen = previousIDs
                cards += incoming.filter { seen.insert($0.id).inserted }
                feedsByID.merge(result.feedsByID) { _, new in new }
            }
            firstItem = result.page.firstItem ?? (reset ? nil : firstItem)
            lastItem = result.page.lastItem ?? (reset ? nil : lastItem)
            pageContext = result.page.pageContext ?? (reset ? nil : pageContext)
            hasMore = result.page.hasMore && incoming.contains { reset || !previousIDs.contains($0.id) }
            page = nextPage
        } catch {
            guard requestID == currentRequest, selectedTab?.key == tab.key, !Task.isCancelled else { return }
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func toggleLike(_ feed: Feed) async {
        do {
            try await service.setLiked(!feed.isLiked, feedID: feed.id)
            guard var updated = feedsByID[feed.id] else { return }
            updated.isLiked = !feed.isLiked
            updated.likes = max(0, feed.likes + (feed.isLiked ? -1 : 1))
            feedsByID[feed.id] = updated
        } catch {
            self.error = error.localizedDescription
        }
    }
}
