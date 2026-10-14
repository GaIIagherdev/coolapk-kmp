import SwiftUI

private struct SearchAppRoute: Hashable {
    let packageName: String
    let title: String
}

private struct SearchProductRoute: Identifiable {
    let id: String
    let title: String
    let imageURL: URL?
}

struct SearchScreen: View {
    let service: CoolapkService

    @Environment(\.colorScheme) private var colorScheme
    @State private var query = ""
    @State private var searchedQuery = ""
    @State private var category: SearchCategory = .all
    @State private var items: [SearchResult] = []
    @State private var feedsByID: [String: Feed] = [:]
    @State private var page = 0
    @State private var firstItem: String?
    @State private var lastItem: String?
    @State private var hasMore = true
    @State private var loading = false
    @State private var error: String?
    @State private var hotWords: [String] = []
    @State private var hotLoading = false
    @State private var hotError: String?
    @State private var selectedFeed: Feed?
    @State private var focusComments = false
    @State private var selectedProduct: SearchProductRoute?
    @State private var requestID = UUID()

    private var accent: Color {
        colorScheme == .dark ? CoolapkStyle.darkGreen : CoolapkStyle.green
    }

    var body: some View {
        VStack(spacing: 0) {
            if !searchedQuery.isEmpty {
                categoryStrip
                Divider()
                resultsContent
            } else {
                hotContent
            }
        }
        .background(CoolapkStyle.canvas.opacity(0.65))
        .navigationTitle("搜索")
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "搜索酷安")
        .onSubmit(of: .search, commitSearch)
        .onChange(of: query) { _, value in
            if value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { clearSearch() }
        }
        .navigationDestination(for: SearchAppRoute.self) { route in
            AppDetailScreen(
                item: AppEntry(id: route.packageName, name: route.title,
                               packageName: route.packageName, iconURL: nil, summary: nil,
                               version: nil, score: nil, downloads: nil),
                service: service
            )
        }
        .sheet(item: $selectedFeed) { feed in
            FeedDetailSheet(feed: feed, service: service, focusComments: focusComments)
        }
        .sheet(item: $selectedProduct) { route in
            DigitalProductDetailSheet(productID: route.id, title: route.title,
                                      imageURL: route.imageURL, service: service)
        }
        .task { if hotWords.isEmpty { await loadHotWords(refresh: false) } }
    }

    private var categoryStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 8) {
                ForEach(SearchCategory.allCases) { option in
                    let selected = category == option
                    Button { selectCategory(option) } label: {
                        Text(option.title)
                            .font(.subheadline.weight(selected ? .semibold : .regular))
                            .foregroundStyle(selected ? Color.white : Color.primary)
                            .padding(.horizontal, 14)
                            .frame(minHeight: 44)
                            .background(selected ? accent : Color(uiColor: .secondarySystemBackground),
                                        in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .frame(height: 60)
    }

    private var hotContent: some View {
        Group {
            if hotWords.isEmpty && hotLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if hotWords.isEmpty {
                ContentUnavailableView {
                    Label(hotError == nil ? "搜索酷安" : "热搜加载失败", systemImage: "magnifyingglass")
                } description: {
                    Text(hotError ?? "输入关键词，或查看最新热搜")
                } actions: {
                    if hotError != nil {
                        Button("重试") { Task { await loadHotWords(refresh: false) } }
                            .buttonStyle(.borderedProminent)
                    }
                }
            } else {
                List {
                    Section {
                        ForEach(Array(hotWords.enumerated()), id: \.offset) { index, word in
                            Button { search(word) } label: {
                                HStack(spacing: 14) {
                                    Text("\(index + 1)")
                                        .font(.subheadline.weight(.semibold))
                                        .monospacedDigit()
                                        .foregroundStyle(index < 3 ? accent : Color.secondary)
                                        .frame(width: 24)
                                    Text(word).foregroundStyle(.primary)
                                    Spacer(minLength: 0)
                                    Image(systemName: "arrow.up.left")
                                        .font(.caption)
                                        .foregroundStyle(.tertiary)
                                }
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    } header: {
                        HStack {
                            Text("热搜")
                            Spacer()
                            Button { Task { await loadHotWords(refresh: true) } } label: {
                                if hotLoading { ProgressView() }
                                else { Image(systemName: "arrow.clockwise") }
                            }
                            .frame(width: 44, height: 44)
                            .disabled(hotLoading)
                            .accessibilityLabel("刷新热搜")
                        }
                    }
                    if let hotError {
                        Button(hotError + " · 重试") { Task { await loadHotWords(refresh: true) } }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
    }

    private var resultsContent: some View {
        Group {
            if items.isEmpty && loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if items.isEmpty {
                ContentUnavailableView {
                    Label(error == nil ? "没有找到相关内容" : "搜索失败", systemImage: "magnifyingglass")
                } description: {
                    Text(error ?? "试试其他关键词或搜索类型")
                } actions: {
                    if error != nil {
                        Button("重试") { Task { await load(reset: true) } }
                            .buttonStyle(.borderedProminent)
                    }
                }
            } else {
                List {
                    ForEach(items) { result in
                        resultRow(result)
                            .listRowSeparator(.hidden)
                            .onAppear {
                                if result.id == items.last?.id && error == nil {
                                    Task { await load(reset: false) }
                                }
                            }
                    }
                    if loading {
                        ProgressView().frame(maxWidth: .infinity).listRowSeparator(.hidden)
                    } else if let error {
                        Button(error + " · 重试") { Task { await load(reset: false) } }
                            .frame(maxWidth: .infinity)
                            .frame(minHeight: 44)
                            .listRowSeparator(.hidden)
                    } else if !hasMore {
                        Text("没有更多结果了")
                            .font(.caption).foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .listRowSeparator(.hidden)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .refreshable { await load(reset: true) }
            }
        }
    }

    @ViewBuilder
    private func resultRow(_ result: SearchResult) -> some View {
        if result.kind == .feed, let feed = feedsByID[result.targetID ?? ""] {
            FeedRow(
                feed: feed,
                onOpen: { focusComments = false; selectedFeed = feed },
                onLike: { Task { await toggleLike(feed) } },
                onReply: { focusComments = true; selectedFeed = feed }
            )
        } else if (result.kind == .app || result.kind == .game), let package = result.targetID {
            NavigationLink(value: SearchAppRoute(packageName: package, title: result.title)) {
                SearchResultRow(result: result, accent: accent)
            }
            .buttonStyle(.plain)
        } else if result.canOpen {
            Button { openResult(result) } label: {
                SearchResultRow(result: result, accent: accent)
            }
            .buttonStyle(.plain)
        } else {
            SearchResultRow(result: result, accent: accent)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(result.kind.title)：\(result.title)，暂无详情页")
        }
    }

    private func openResult(_ result: SearchResult) {
        guard let target = result.targetID else { return }
        switch result.kind {
        case .feed:
            focusComments = false
            selectedFeed = feedsByID[target] ?? Feed(
                id: target, title: result.title, message: result.subtitle ?? result.title,
                username: "酷友", avatarURL: nil, device: nil, timestamp: nil,
                likes: 0, replies: 0, imageURLs: result.imageURL.map { [$0] } ?? [], isLiked: false
            )
        case .product:
            selectedProduct = SearchProductRoute(id: target, title: result.title, imageURL: result.imageURL)
        default:
            break
        }
    }

    private func commitSearch() {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { clearSearch(); return }
        searchedQuery = term
        Task { await load(reset: true) }
    }

    private func search(_ word: String) {
        query = word
        category = .all
        searchedQuery = word
        Task { await load(reset: true) }
    }

    private func clearSearch() {
        requestID = UUID()
        searchedQuery = ""
        items = []
        feedsByID = [:]
        page = 0
        firstItem = nil
        lastItem = nil
        hasMore = true
        loading = false
        error = nil
    }

    private func selectCategory(_ option: SearchCategory) {
        guard category != option else { return }
        category = option
        if !searchedQuery.isEmpty { Task { await load(reset: true) } }
    }

    private func load(reset: Bool) async {
        guard !searchedQuery.isEmpty else { return }
        if !reset { guard !loading, hasMore else { return } }
        let token = reset ? UUID() : requestID
        let currentQuery = searchedQuery
        let currentCategory = category
        if reset {
            requestID = token
            items = []
            feedsByID = [:]
            page = 0
            firstItem = nil
            lastItem = nil
            hasMore = true
        }
        loading = true
        error = nil
        do {
            let response = try await service.searchByType(
                currentCategory, query: currentQuery, page: page + 1,
                firstItem: reset ? nil : firstItem, lastItem: reset ? nil : lastItem
            )
            guard requestID == token, searchedQuery == currentQuery, category == currentCategory else { return }
            let existing = Set(items.map(\.id))
            let fresh = reset ? response.page.items : response.page.items.filter { !existing.contains($0.id) }
            items = reset ? fresh : items + fresh
            feedsByID.merge(response.feedsByID) { _, newer in newer }
            firstItem = response.page.firstItem ?? firstItem
            lastItem = response.page.lastItem ?? lastItem
            hasMore = response.page.hasMore && !fresh.isEmpty
            page += 1
        } catch {
            guard requestID == token else { return }
            self.error = error.localizedDescription
        }
        if requestID == token { loading = false }
    }

    private func loadHotWords(refresh: Bool) async {
        guard !hotLoading else { return }
        hotLoading = true
        hotError = nil
        do {
            hotWords = try await service.hotSearches(refresh: refresh)
        } catch {
            hotError = error.localizedDescription
        }
        hotLoading = false
    }

    private func toggleLike(_ feed: Feed) async {
        do {
            try await service.setLiked(!feed.isLiked, feedID: feed.id)
            var updated = feed
            updated.isLiked = !feed.isLiked
            updated.likes = max(0, feed.likes + (feed.isLiked ? -1 : 1))
            feedsByID[feed.id] = updated
        } catch let failure {
            error = failure.localizedDescription
        }
    }
}

private struct SearchResultRow: View {
    let result: SearchResult
    let accent: Color

    var body: some View {
        HStack(spacing: 12) {
            RemoteImage(url: result.imageURL, fallback: result.kind.symbol)
                .frame(width: 48, height: 48)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 4) {
                Text(result.kind.title)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(accent)
                Text(result.title.plainText)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                if let subtitle = result.subtitle?.plainText, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                if !result.canOpen {
                    Text("暂无详情页")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
            }
            Spacer(minLength: 4)
            if result.canOpen {
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
        .contentShape(Rectangle())
    }
}
