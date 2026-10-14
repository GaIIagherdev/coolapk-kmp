import SwiftUI

struct DigitalScreen: View {
    let service: CoolapkService

    @Environment(\.openURL) private var openURL
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var tabs: [DigitalTab] = []
    @State private var selectedTab: DigitalTab?
    @State private var mode: DigitalMode = .brand
    @State private var sideItems: [DigitalEntity] = []
    @State private var selectedSide: DigitalEntity?
    @State private var items: [DigitalEntity] = []
    @State private var page = 0
    @State private var firstItem: String?
    @State private var lastItem: String?
    @State private var pageContext: String?
    @State private var hasMore = true
    @State private var loading = true
    @State private var error: String?
    @State private var sideLoading = false
    @State private var sideError: String?
    @State private var selectedProduct: DigitalEntity?
    @State private var requestID = UUID()
    @State private var sideRequestID = UUID()

    private var accent: Color { CoolapkStyle.green }
    private var isWide: Bool { horizontalSizeClass == .regular }

    var body: some View {
        Group {
            if tabs.isEmpty && loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if tabs.isEmpty {
                ContentUnavailableView {
                    Label("数码栏目暂不可用", systemImage: "cpu")
                } description: {
                    Text(error ?? "请稍后重试")
                } actions: {
                    Button("重试") { Task { await loadConfig() } }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                configuredContent
            }
        }
        .navigationTitle("数码")
        .task { if tabs.isEmpty { await loadConfig() } }
        .sheet(item: $selectedProduct) { product in
            DigitalProductDetailSheet(product: product, service: service)
        }
    }

    private var configuredContent: some View {
        VStack(spacing: 0) {
            tabStrip
            Divider()
            if selectedTab?.category == true {
                categoryContent
            } else {
                resultContent
            }
        }
        .background(CoolapkStyle.canvas.opacity(0.65))
    }

    private var tabStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 8) {
                ForEach(tabs) { tab in
                    Button { selectTab(tab) } label: {
                        Text(tab.title)
                            .font(.subheadline.weight(selectedTab?.key == tab.key ? .semibold : .regular))
                            .foregroundStyle(selectedTab?.key == tab.key ? Color.white : Color.primary)
                            .padding(.horizontal, 14)
                            .frame(minHeight: 36)
                            .background(selectedTab?.key == tab.key ? accent : Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selectedTab?.key == tab.key ? .isSelected : [])
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .frame(height: 56)
    }

    private var categoryContent: some View {
        VStack(spacing: 0) {
            Picker("数码分类模式", selection: $mode) {
                Text("品牌").tag(DigitalMode.brand)
                Text("分类").tag(DigitalMode.category)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            if isWide {
                HStack(alignment: .top, spacing: 0) {
                    sideList.frame(width: 220)
                    Divider()
                    resultContent
                }
            } else {
                if !sideItems.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        LazyHStack(spacing: 8) {
                            ForEach(sideItems) { side in
                                Button { selectSide(side) } label: {
                                    Text(side.title ?? "未命名")
                                        .font(.caption.weight(selectedSide?.id == side.id ? .semibold : .regular))
                                        .foregroundStyle(selectedSide?.id == side.id ? Color.white : Color.primary)
                                        .padding(.horizontal, 12)
                                        .frame(minHeight: 34)
                                        .background(selectedSide?.id == side.id ? accent : Color(uiColor: .secondarySystemBackground), in: Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 10)
                    }
                    .frame(height: 44)
                }
                resultContent
            }
        }
        .task(id: "\(selectedTab?.key ?? ""):\(mode.rawValue)") { await loadSide() }
    }

    private var sideList: some View {
        Group {
            if sideLoading {
                ProgressView().frame(maxWidth: .infinity).padding(.vertical, 20)
            } else if let sideError {
                VStack(spacing: 8) {
                    Text(sideError).font(.caption).foregroundStyle(.secondary)
                    Button("重试") { Task { await loadSide() } }
                        .buttonStyle(.bordered)
                }
                .frame(maxWidth: .infinity)
                .padding()
            } else if sideItems.isEmpty {
                Text("暂无品牌或分类").font(.caption).foregroundStyle(.secondary).padding()
            } else {
                List(sideItems) { side in
                    Button { selectSide(side) } label: {
                        HStack(spacing: 10) {
                            RemoteImage(url: side.imageURL, fallback: "cpu")
                                .frame(width: 32, height: 32)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            Text(side.title ?? "未命名").lineLimit(1)
                            Spacer()
                            if selectedSide?.id == side.id {
                                Image(systemName: "checkmark").foregroundStyle(accent)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
                .listStyle(.plain)
            }
        }
    }

    private var resultContent: some View {
        Group {
            if items.isEmpty && (loading || sideLoading) {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if items.isEmpty {
                ContentUnavailableView {
                    Label(error == nil ? "暂无数码内容" : "数码内容加载失败", systemImage: "cpu")
                } description: {
                    Text(sideError ?? error ?? "下拉刷新后再试")
                } actions: {
                    Button("重试") {
                        Task {
                            if selectedTab?.category == true && (sideError != nil || selectedSide == nil) {
                                await loadSide()
                            } else {
                                await loadPage(reset: true)
                            }
                        }
                    }
                        .buttonStyle(.borderedProminent)
                }
                .frame(maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(items) { entity in
                            DigitalEntityCard(entity: entity, onOpen: openEntity)
                        }
                        if loading {
                            ProgressView().frame(maxWidth: .infinity).padding()
                        } else if let error {
                            Button(error + " · 点击重试") { Task { await loadPage(reset: false) } }
                                .frame(maxWidth: .infinity)
                        } else if hasMore {
                            Button("加载更多") { Task { await loadPage(reset: false) } }
                                .frame(maxWidth: .infinity)
                                .frame(minHeight: 44)
                        } else {
                            Text("没有更多内容了").font(.caption).foregroundStyle(.secondary)
                                .frame(maxWidth: .infinity).padding(.vertical, 14)
                        }
                    }
                    .frame(maxWidth: 820, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity)
                }
                .refreshable { await loadPage(reset: true) }
            }
        }
    }

    private func loadConfig() async {
        loading = true
        error = nil
        do {
            let config = try await service.digitalConfig()
            tabs = config.tabs
            selectedTab = config.tabs.first(where: { $0.key == config.selectedKey }) ?? config.tabs.first
            if selectedTab?.category == true { mode = .brand; loading = false }
            else { await loadPage(reset: true) }
        } catch {
            self.error = error.localizedDescription
            loading = false
        }
    }

    private func selectTab(_ tab: DigitalTab) {
        guard selectedTab?.key != tab.key else { return }
        requestID = UUID()
        sideRequestID = UUID()
        selectedTab = tab
        selectedSide = nil
        sideItems = []
        sideLoading = false
        sideError = nil
        items = []
        error = nil
        page = 0
        firstItem = nil
        lastItem = nil
        pageContext = nil
        hasMore = true
        loading = true
        if !tab.category { Task { await loadPage(reset: true) } }
    }

    private func loadSide() async {
        guard let tab = selectedTab, tab.category else { return }
        let currentMode = mode
        let token = UUID()
        sideRequestID = token
        requestID = UUID()
        sideLoading = true
        sideError = nil
        selectedSide = nil
        sideItems = []
        items = []
        loading = true
        error = nil
        do {
            let options = currentMode == .brand ? try await service.digitalBrands() : try await service.digitalCategories()
            guard sideRequestID == token, selectedTab?.key == tab.key, mode == currentMode, !Task.isCancelled else { return }
            var seen = Set<String>()
            sideItems = options.filter { seen.insert($0.id).inserted }
            selectedSide = sideItems.first
            sideLoading = false
            if selectedSide != nil { await loadPage(reset: true) }
            else { loading = false }
        } catch {
            guard sideRequestID == token, !Task.isCancelled else { return }
            sideError = error.localizedDescription
            loading = false
        }
        if sideRequestID == token { sideLoading = false }
    }

    private func selectSide(_ side: DigitalEntity) {
        guard selectedSide?.id != side.id else { return }
        requestID = UUID()
        selectedSide = side
        items = []
        loading = true
        Task { await loadPage(reset: true) }
    }

    private func loadPage(reset: Bool) async {
        guard let tab = selectedTab else { return }
        if tab.category && selectedSide == nil { loading = false; return }
        if !reset { guard !loading, hasMore else { return } }
        let token = UUID()
        requestID = token
        if reset {
            items = []
            page = 0
            firstItem = nil
            lastItem = nil
            pageContext = nil
            hasMore = true
        }
        loading = true
        error = nil
        do {
            let result: DigitalPage
            if tab.category, let side = selectedSide {
                if mode == .brand {
                    result = try await service.digitalBrandProducts(
                        brandID: side.id, brandType: side.brandType ?? "recommend",
                        page: page + 1, firstItem: reset ? nil : firstItem, lastItem: reset ? nil : lastItem
                    )
                } else {
                    result = try await service.digitalCategoryProducts(
                        category: side, page: page + 1,
                        firstItem: reset ? nil : firstItem, lastItem: reset ? nil : lastItem
                    )
                }
            } else {
                result = try await service.digitalPage(
                    tab: tab, page: page + 1,
                    firstItem: reset ? nil : firstItem, lastItem: reset ? nil : lastItem,
                    pageContext: reset ? nil : pageContext
                )
            }
            guard requestID == token, selectedTab?.key == tab.key else { return }
            let existing = Set(items.map(\.id))
            let fresh = reset ? result.items : result.items.filter { !existing.contains($0.id) }
            items = reset ? fresh : items + fresh
            page += 1
            firstItem = result.firstItem ?? firstItem
            lastItem = result.lastItem ?? lastItem
            pageContext = result.pageContext ?? pageContext
            hasMore = result.hasMore && !fresh.isEmpty
        } catch {
            guard requestID == token else { return }
            self.error = error.localizedDescription
        }
        if requestID == token { loading = false }
    }

    private func openEntity(_ entity: DigitalEntity) {
        if let destination = tabs.first(where: { tab in
            let target = entity.url?.replacingOccurrences(of: "#", with: "")
            return target != nil && (target == tab.url || target == tab.pageName || target == tab.key)
        }) {
            selectTab(destination)
            return
        }
        if let productID = productID(for: entity) {
            selectedProduct = entity.withProductID(productID)
        } else if let url = entity.url.flatMap(makeURL) {
            openURL(url)
        }
    }

    private func productID(for entity: DigitalEntity) -> String? {
        if let productID = entity.productID, !productID.isEmpty { return productID }
        guard let raw = entity.url else {
            return entity.entityType?.localizedCaseInsensitiveContains("product") == true ? entity.id : nil
        }
        let normalized = raw.hasPrefix("#/") ? "https://www.coolapk.com/\(raw.dropFirst(2))" : raw
        guard let components = URLComponents(string: normalized),
              components.path.lowercased().contains("product/detail") else {
            return entity.entityType?.localizedCaseInsensitiveContains("product") == true ? entity.id : nil
        }
        return components.queryItems?.first(where: { ["id", "productid"].contains($0.name.lowercased()) })?.value
            ?? entity.id
    }

    private func makeURL(_ raw: String) -> URL? {
        if raw.hasPrefix("http://") || raw.hasPrefix("https://") { return URL(string: raw) }
        if raw.hasPrefix("#/") { return URL(string: "https://www.coolapk.com/\(raw.dropFirst(2))") }
        if raw.hasPrefix("/") { return URL(string: "https://www.coolapk.com\(raw)") }
        return nil
    }
}

private enum DigitalMode: String {
    case brand
    case category
}

private struct DigitalEntityCard: View {
    let entity: DigitalEntity
    let onOpen: (DigitalEntity) -> Void

    private var template: String { (entity.entityTemplate ?? entity.entityType ?? "").lowercased() }
    private var children: [DigitalEntity] { entity.entities }
    private var isCarousel: Bool { !children.isEmpty && template.hasPrefix("imagecarousel") }
    private var isGrid: Bool { !children.isEmpty && ["iconlinkgridcard", "iconlongtitlegridcard", "icongridcard", "iconminigridcard"].contains(template) }
    private var isRows: Bool { !children.isEmpty && (template == "iconlistcard" || template == "producttimelinelistcard" || children.allSatisfy { $0.productID != nil }) }
    private var columns: [GridItem] {
        let count = template == "iconlinkgridcard" ? 5 : min(4, max(2, children.count))
        return Array(repeating: GridItem(.flexible(), spacing: 8), count: count)
    }

    var body: some View {
        if isCarousel {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 8) {
                    ForEach(children) { child in
                        Button { onOpen(child) } label: {
                            RemoteImage(url: child.imageURL, fallback: "photo")
                                .frame(width: 280, height: 90)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        } else if isGrid {
            cardContainer {
                if let title = entity.title, !title.isEmpty { cardTitle(title) }
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(children) { child in gridItem(child) }
                }
            }
        } else if isRows {
            cardContainer {
                if let title = entity.title, !title.isEmpty { cardTitle(title) }
                LazyVStack(spacing: 0) {
                    ForEach(children) { child in rowItem(child) }
                }
            }
        } else if !children.isEmpty {
            cardContainer {
                if let title = entity.title, !title.isEmpty { cardTitle(title) }
                ForEach(children) { child in
                    Button { onOpen(child) } label: {
                        HStack(spacing: 10) {
                            RemoteImage(url: child.imageURL, fallback: "cpu")
                                .frame(width: 42, height: 42)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            Text(child.title ?? "查看内容").font(.subheadline)
                            Spacer()
                            Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(.vertical, 6)
                }
            }
        } else {
            Button { onOpen(entity) } label: {
                HStack(spacing: 10) {
                    RemoteImage(url: entity.imageURL, fallback: "cpu")
                        .frame(width: 52, height: 52)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    Text(entity.title ?? "查看内容").font(.headline)
                    Spacer()
                    Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                }
                .padding(12)
                .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
    }

    private func cardContainer<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10, content: content)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
    }

    private func cardTitle(_ title: String) -> some View {
        Text(title).font(.headline)
    }

    private func gridItem(_ child: DigitalEntity) -> some View {
        Button { onOpen(child) } label: {
            VStack(spacing: 6) {
                RemoteImage(url: child.imageURL, fallback: "cpu")
                    .frame(width: 48, height: 48)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                Text(child.title ?? "未命名")
                    .font(.caption)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func rowItem(_ child: DigitalEntity) -> some View {
        Button { onOpen(child) } label: {
            HStack(spacing: 10) {
                RemoteImage(url: child.imageURL, fallback: "cpu")
                    .frame(width: 44, height: 44)
                    .clipShape(RoundedRectangle(cornerRadius: 9))
                VStack(alignment: .leading, spacing: 3) {
                    Text(child.title ?? "未命名").font(.subheadline.weight(.semibold)).lineLimit(1)
                    if let subTitle = child.subTitle, !subTitle.isEmpty {
                        Text(subTitle).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                    }
                }
                Spacer()
                if let price = child.price { Text(price).font(.caption.weight(.semibold)).foregroundStyle(CoolapkStyle.green) }
            }
            .padding(.vertical, 7)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct DigitalProductDetailSheet: View {
    let product: DigitalEntity
    let service: CoolapkService
    @Environment(\.dismiss) private var dismiss
    @State private var detail: DigitalEntity?
    @State private var loading = true

    init(product: DigitalEntity, service: CoolapkService) {
        self.product = product
        self.service = service
    }

    init(productID: String, title: String, imageURL: URL?, service: CoolapkService) {
        self.product = DigitalEntity(
            id: productID, entityType: "product", entityTemplate: nil,
            title: title, subTitle: nil, imageURL: imageURL, url: nil,
            productID: productID, brandType: nil, productSpecs: [], hot: nil,
            release: nil, price: nil, rating: nil, extra: [:], entities: []
        )
        self.service = service
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                let item = detail ?? product
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 14) {
                        RemoteImage(url: item.imageURL, fallback: "cpu")
                            .frame(width: 76, height: 76)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                        Text(item.title ?? "数码产品").font(.title2.weight(.semibold))
                    }
                    if loading { ProgressView() }
                    if let subTitle = item.subTitle, !subTitle.isEmpty { Text(subTitle).foregroundStyle(.secondary) }
                    if !item.productSpecs.isEmpty {
                        Text(item.productSpecs.joined(separator: " · ")).font(.subheadline).foregroundStyle(.secondary)
                    }
                    if let price = item.price { detailRow("价格", price) }
                    if let rating = item.rating { detailRow("评分", rating) }
                    if let release = item.release { detailRow("上市", release) }
                }
                .frame(maxWidth: 700, alignment: .leading)
                .padding(20)
                .frame(maxWidth: .infinity)
            }
            .navigationTitle("产品详情")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
            .task {
                guard let id = product.productID ?? product.id.nilIfNonNumeric else { loading = false; return }
                detail = try? await service.digitalProductDetail(id)
                loading = false
            }
        }
    }

    private func detailRow(_ title: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).foregroundStyle(.secondary).frame(width: 44, alignment: .leading)
            Text(value).frame(maxWidth: .infinity, alignment: .leading)
        }
        .font(.subheadline)
    }
}

private extension DigitalEntity {
    func withProductID(_ value: String) -> DigitalEntity {
        DigitalEntity(id: id, entityType: entityType, entityTemplate: entityTemplate, title: title,
                      subTitle: subTitle, imageURL: imageURL, url: url, productID: value,
                      brandType: brandType,
                      productSpecs: productSpecs, hot: hot, release: release, price: price,
                      rating: rating, extra: extra, entities: entities)
    }
}

private extension String {
    var nilIfNonNumeric: String? { !isEmpty && allSatisfy(\.isNumber) ? self : nil }
}
