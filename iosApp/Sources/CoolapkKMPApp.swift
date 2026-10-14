import SwiftUI

enum CoolapkStyle {
    static let green = Color(red: 0.02, green: 0.52, blue: 0.29)
    static let darkGreen = Color(red: 0.13, green: 0.78, blue: 0.46)
    static let canvas = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.035, green: 0.075, blue: 0.055, alpha: 1)
            : UIColor(red: 0.976, green: 0.988, blue: 0.976, alpha: 1)
    })
    static let ink = Color(red: 0.06, green: 0.13, blue: 0.10)
}

struct CoolapkActionButtonStyle: ViewModifier {
    var prominent = false
    @Environment(\.colorScheme) private var colorScheme

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 26, *) {
            if prominent {
                content.buttonStyle(.glassProminent)
                    .foregroundStyle(colorScheme == .dark ? CoolapkStyle.ink : .white)
            } else {
                content.buttonStyle(.glass)
            }
        } else if prominent {
            content.buttonStyle(.borderedProminent)
                .foregroundStyle(colorScheme == .dark ? CoolapkStyle.ink : .white)
        } else {
            content.buttonStyle(.bordered)
        }
    }
}

@main
struct CoolapkKMPApp: App {
    var body: some Scene {
        WindowGroup {
            if let service = try? CoolapkService() {
                AppRoot(service: service)
            } else {
                ContentUnavailableView("无法启动", systemImage: "exclamationmark.triangle", description: Text("API 核心初始化失败"))
            }
        }
    }
}

private struct AppRoot: View {
    let service: CoolapkService
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        TabView {
            NavigationStack { TimelineScreen(service: service, source: .home) }
                .tabItem { Label("首页", systemImage: "house.fill") }
            NavigationStack { DigitalScreen(service: service) }
                .tabItem { Label("数码", systemImage: "cpu") }
            NavigationStack { PublishScreen(service: service) }
                .tabItem { Label("发布", systemImage: "plus.circle.fill") }
            NavigationStack { DiscoveryScreen(service: service) }
                .tabItem { Label("发现", systemImage: "safari") }
            NavigationStack { ProfileScreen(service: service) }
                .tabItem { Label("我", systemImage: "person.crop.circle") }
        }
        .tint(colorScheme == .dark ? CoolapkStyle.darkGreen : CoolapkStyle.green)
    }
}

struct TimelineScreen: View {
    let service: CoolapkService
    let source: FeedSource
    @State private var items: [Feed] = []
    @State private var page = 0
    @State private var firstItem: String?
    @State private var lastItem: String?
    @State private var hasMore = true
    @State private var loading = false
    @State private var error: String?
    @State private var selectedFeed: Feed?
    @State private var focusComments = false

    var body: some View {
        Group {
            if items.isEmpty && loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if items.isEmpty {
                ContentUnavailableView(error == nil ? "暂无动态" : "加载失败", systemImage: "text.bubble", description: Text(error ?? "下拉刷新后再试"))
                    .overlay(alignment: .bottom) { if error != nil { retryButton } }
            } else {
                feedList
            }
        }
        .background(CoolapkStyle.canvas.opacity(0.65))
        .navigationTitle(source.rawValue)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if source == .home { Text("酷安").fontWeight(.bold).foregroundStyle(CoolapkStyle.green) }
            }
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink { SearchScreen(service: service) } label: {
                    Image(systemName: "magnifyingglass").accessibilityLabel("搜索酷安")
                }
            }
        }
        .sheet(item: $selectedFeed) { feed in
            FeedDetailSheet(feed: feed, service: service, focusComments: focusComments)
        }
        .task { if page == 0 { await load(reset: true) } }
    }

    private var feedList: some View {
        List {
            ForEach(items) { feed in
                FeedRow(
                    feed: feed,
                    onOpen: { focusComments = false; selectedFeed = feed },
                    onLike: { Task { await toggleLike(feed) } },
                    onReply: { focusComments = true; selectedFeed = feed }
                )
                .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
                .listRowSeparator(.hidden)
                .onAppear { if feed.id == items.last?.id { Task { await load(reset: false) } } }
            }
            if loading { ProgressView().frame(maxWidth: .infinity).listRowSeparator(.hidden) }
            if let error {
                Button(error + " · 点击重试") { Task { await load(reset: false) } }
                    .frame(maxWidth: .infinity).listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .refreshable { await load(reset: true) }
    }

    private var retryButton: some View {
        Button("重试") { Task { await load(reset: true) } }
            .buttonStyle(.borderedProminent)
            .padding(.bottom, 40)
    }

    private func load(reset: Bool) async {
        guard !loading, reset || hasMore else { return }
        loading = true
        error = nil
        let nextPage = reset ? 1 : page + 1
        do {
            let result = try await service.feed(source, page: nextPage,
                                                first: reset ? nil : firstItem,
                                                last: reset ? nil : lastItem)
            if reset { items = result.items } else { items = appendUnique(items, result.items) }
            firstItem = reset ? result.firstItem : (result.firstItem ?? firstItem)
            lastItem = reset ? result.lastItem : (result.lastItem ?? lastItem)
            hasMore = result.hasMore && !result.items.isEmpty
            page = nextPage
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }

    private func toggleLike(_ feed: Feed) async {
        do {
            try await service.setLiked(!feed.isLiked, feedID: feed.id)
            guard let index = items.firstIndex(where: { $0.id == feed.id }) else { return }
            items[index].isLiked = !feed.isLiked
            items[index].likes = max(0, feed.likes + (feed.isLiked ? -1 : 1))
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private struct AppsScreen: View {
    let service: CoolapkService
    @State private var category = "recommend"
    @State private var items: [AppEntry] = []
    @State private var page = 0
    @State private var hasMore = true
    @State private var loading = false
    @State private var error: String?
    @State private var requestID = UUID()
    private let categories = [("recommend", "推荐"), ("newest", "最新"), ("tools", "工具"), ("social", "社交")]

    var body: some View {
        VStack(spacing: 0) {
            Picker("分类", selection: $category) {
                ForEach(categories, id: \.0) { value in Text(value.1).tag(value.0) }
            }
            .pickerStyle(.segmented)
            .padding(16)
            if items.isEmpty && loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if items.isEmpty {
                ContentUnavailableView(error == nil ? "暂无应用" : "加载失败", systemImage: "square.grid.2x2", description: Text(error ?? "下拉刷新后再试"))
                    .frame(maxHeight: .infinity)
                    .overlay(alignment: .bottom) {
                        if error != nil {
                            Button("重试") { Task { await load(reset: true) } }
                                .buttonStyle(.borderedProminent)
                                .padding(.bottom, 40)
                        }
                    }
            } else {
                List {
                    ForEach(items) { item in
                        NavigationLink {
                            AppDetailScreen(item: item, service: service)
                        } label: {
                            AppRow(item: item)
                        }
                        .listRowSeparator(.hidden)
                        .onAppear { if item.id == items.last?.id { Task { await load(reset: false) } } }
                    }
                    if loading { ProgressView().frame(maxWidth: .infinity) }
                    if let error {
                        Button(error + " · 点击重试") { Task { await load(reset: false) } }
                    }
                }
                .listStyle(.plain)
                .refreshable { await load(reset: true) }
            }
        }
        .navigationTitle("应用")
        .task(id: category) { await load(reset: true) }
    }

    private func load(reset: Bool) async {
        if !reset { guard !loading, hasMore else { return } }
        let currentRequest = reset ? UUID() : requestID
        if reset { requestID = currentRequest; page = 0; items = [] }
        loading = true
        error = nil
        let requestedCategory = category
        do {
            let result = try await service.apps(page: reset ? 1 : page + 1, category: requestedCategory)
            guard category == requestedCategory, requestID == currentRequest else { return }
            items = reset ? result.items : appendUnique(items, result.items)
            hasMore = result.hasMore && !result.items.isEmpty
            page = reset ? 1 : page + 1
        } catch {
            guard requestID == currentRequest else { return }
            self.error = error.localizedDescription
        }
        loading = false
    }
}

private struct ProfileScreen: View {
    let service: CoolapkService
    @State private var profile: Profile?
    @State private var loggedIn = false
    @State private var notifications = 0
    @State private var loading = true
    @State private var error: String?
    @State private var actionError: String?
    @State private var showingLogin: LoginMethod?
    @State private var showingLogout = false

    var body: some View {
        Group {
            if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let profile {
                List {
                    VStack(spacing: 12) {
                        RemoteImage(url: profile.avatarURL, fallback: "person.crop.circle.fill")
                            .frame(width: 88, height: 88).clipShape(Circle())
                        Text(profile.username).font(.title2.bold())
                        if let bio = profile.bio { Text(bio).foregroundStyle(.secondary) }
                        HStack(spacing: 36) {
                            statistic("动态", profile.feeds)
                            statistic("关注", profile.following)
                            statistic("粉丝", profile.followers)
                        }
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 22)
                    .listRowSeparator(.hidden)
                    NavigationLink {
                        NotificationsScreen(service: service)
                    } label: {
                        Label("通知", systemImage: "bell.badge")
                    }
                    .badge(notifications)
                    if let error {
                        Text(error).foregroundStyle(.red)
                        Button("重试") { Task { await reload() } }
                    }
                    Button("退出登录", role: .destructive) { showingLogout = true }
                }
            } else if loggedIn {
                ContentUnavailableView {
                    Label("资料暂不可用", systemImage: "person.crop.circle.badge.exclamationmark")
                } description: {
                    Text(error ?? "请稍后重试")
                } actions: {
                    Button("重试") { Task { await reload() } }.buttonStyle(.borderedProminent)
                    NavigationLink("查看通知") { NotificationsScreen(service: service) }
                    Button("退出登录") { showingLogout = true }
                }
            } else {
                ContentUnavailableView {
                    Label("登录酷安", systemImage: "person.crop.circle")
                } description: {
                    Text(error ?? "登录后可查看个人资料与通知")
                } actions: {
                    Button {
                        showingLogin = .web
                    } label: {
                        Label("酷安网页登录", systemImage: "lock.shield")
                    }
                    .modifier(CoolapkActionButtonStyle(prominent: true))
                    Button("导入 Cookie") { showingLogin = .cookie }
                }
            }
        }
        .navigationTitle("我")
        .sheet(item: $showingLogin) { method in
            LoginSheet(service: service, initialMethod: method) { Task { await reload() } }
        }
        .confirmationDialog("退出当前账号？", isPresented: $showingLogout) {
            Button("退出登录", role: .destructive) {
                Task {
                    do { try await service.logout(); await reload() }
                    catch { actionError = error.localizedDescription }
                }
            }
        }
        .alert("操作失败", isPresented: Binding(
            get: { actionError != nil },
            set: { if !$0 { actionError = nil } }
        )) {
            Button("确定", role: .cancel) { actionError = nil }
        } message: {
            Text(actionError ?? "")
        }
        .task { await reload() }
    }

    private func statistic(_ title: String, _ count: Int) -> some View {
        VStack(spacing: 4) {
            Text(count.formatted()).font(.headline)
            Text(title).font(.caption).foregroundStyle(.secondary)
        }
    }

    private func reload() async {
        loading = true
        profile = nil
        notifications = 0
        error = nil
        do {
            loggedIn = await service.isLoggedIn()
            profile = loggedIn ? try await service.profile() : nil
            notifications = profile == nil ? 0 : (try await service.notificationCount())
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

enum LoginMethod: String, CaseIterable, Identifiable {
    case web = "网页授权"
    case cookie = "Cookie 导入"

    var id: String { rawValue }
}

struct LoginSheet: View {
    let service: CoolapkService
    let onSuccess: () -> Void
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    @StateObject private var webLogin: OfficialLoginSession
    @State private var method: LoginMethod
    @State private var cookie = ""
    @State private var loading = false
    @State private var error: String?

    init(service: CoolapkService, initialMethod: LoginMethod = .web, onSuccess: @escaping () -> Void) {
        self.service = service
        self.onSuccess = onSuccess
        _method = State(initialValue: initialMethod)
        _webLogin = StateObject(wrappedValue: OfficialLoginSession(service: service))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("登录方式", selection: $method) {
                    ForEach(LoginMethod.allCases) { method in
                        Text(method.rawValue).tag(method)
                    }
                }
                .pickerStyle(.segmented)
                .disabled(webLogin.isVerifying || loading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                if method == .web {
                    webLoginContent
                } else {
                    cookieLoginContent
                }
            }
            .navigationTitle("网页登录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                        .disabled(webLogin.isVerifying || loading)
                }
                if method == .cookie {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("登录") { Task { await login() } }
                            .disabled(cookie.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || loading)
                    }
                }
            }
            .overlay { if loading { ProgressView() } }
        }
        .tint(colorScheme == .dark ? CoolapkStyle.darkGreen : CoolapkStyle.green)
        .presentationDetents([.large])
        .interactiveDismissDisabled(webLogin.isVerifying || loading)
        .onAppear { if method == .web { webLogin.start() } }
        .onChange(of: method) { _, method in webLogin.setActive(method == .web) }
        .onDisappear { webLogin.cancel() }
        .onChange(of: webLogin.authenticated) { _, authenticated in
            guard authenticated else { return }
            cookie = ""
            onSuccess()
            dismiss()
        }
    }

    private var webLoginContent: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: "lock.shield.fill")
                    .foregroundStyle(webLogin.isSecurePage
                        ? (webLogin.isCoolapkPage ? CoolapkStyle.green : Color.secondary)
                        : Color.orange)
                Text(webLogin.pageHost)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Spacer()
                if webLogin.isLoading || webLogin.isVerifying { ProgressView().controlSize(.small) }
                Button {
                    webLogin.reload()
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .buttonStyle(.plain)
                .disabled(webLogin.isVerifying)
                .accessibilityLabel("重新加载登录页")
                .help("重新加载登录页")
            }
            .font(.caption)
            .padding(.horizontal, 16)
            .frame(height: 36)

            OfficialLoginWebView(webView: webLogin.webView)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            if let error = webLogin.error {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
            }
            Button {
                webLogin.completeManually()
            } label: {
                Label(webLogin.isVerifying ? "正在校验" : "已完成登录", systemImage: "checkmark.circle.fill")
                    .frame(maxWidth: .infinity)
            }
            .modifier(CoolapkActionButtonStyle(prominent: true))
            .disabled(webLogin.isVerifying)
            .padding(16)
        }
        .background(CoolapkStyle.canvas.opacity(0.5))
    }

    private var cookieLoginContent: some View {
        Form {
            Section("会话 Cookie") {
                SecureField("粘贴包含 SESSID 的 Cookie", text: $cookie)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .privacySensitive()
                    .accessibilityLabel("会话 Cookie")
            }
            if let error { Text(error).foregroundStyle(.red) }
        }
    }

    private func login() async {
        loading = true
        do {
            try await service.login(cookie: cookie)
            cookie = ""
            onSuccess()
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

private func appendUnique<Item: Identifiable>(_ existing: [Item], _ incoming: [Item]) -> [Item] where Item.ID == String {
    var seen = Set(existing.map(\.id))
    return existing + incoming.filter { seen.insert($0.id).inserted }
}
