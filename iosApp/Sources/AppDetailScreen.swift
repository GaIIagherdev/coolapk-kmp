import SwiftUI

struct AppDetailScreen: View {
    let item: AppEntry
    let service: CoolapkService

    @State private var detail: AppDetail?
    @State private var loading = true
    @State private var error: String?
    @State private var imagePresentation: ImageViewerPresentation?

    var body: some View {
        Group {
            if let detail {
                detailContent(detail)
            } else if loading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ContentUnavailableView {
                    Label("应用详情暂不可用", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(error ?? "请稍后重试")
                } actions: {
                    Button("重试") { Task { await load() } }
                        .buttonStyle(.borderedProminent)
                }
            }
        }
        .navigationTitle(item.name)
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .fullScreenCover(item: $imagePresentation) { presentation in
            ImageViewer(presentation: presentation)
        }
    }

    private func detailContent(_ detail: AppDetail) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if let error {
                    Text(error).font(.subheadline).foregroundStyle(.red)
                }
                header(detail)
                Divider()
                metadata(detail)
                if let url = externalURL(detail) {
                    Link(destination: url) {
                        Label("在酷安打开", systemImage: "arrow.up.right")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
                if !detail.screenshotURLs.isEmpty {
                    Divider()
                    VStack(alignment: .leading, spacing: 12) {
                        Text("截图").font(.title3.weight(.semibold))
                        ScrollView(.horizontal, showsIndicators: false) {
                            LazyHStack(spacing: 12) {
                                ForEach(Array(detail.screenshotURLs.enumerated()), id: \.offset) { index, url in
                                    Button {
                                        imagePresentation = ImageViewerPresentation(
                                            urls: detail.screenshotURLs, initialIndex: index
                                        )
                                    } label: {
                                        RemoteImage(url: url, fallback: "photo", contentMode: .fit)
                                            .frame(width: 170, height: 300)
                                            .background(Color(uiColor: .secondarySystemBackground))
                                            .clipShape(RoundedRectangle(cornerRadius: 8))
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel("查看第 \(index + 1) 张应用截图")
                                }
                            }
                        }
                    }
                }
                if let overview = detail.overview?.plainText, !overview.isEmpty {
                    Divider()
                    textSection("应用介绍", overview)
                }
                if let changeLog = detail.changeLog?.plainText, !changeLog.isEmpty {
                    Divider()
                    textSection("更新日志", changeLog)
                }
            }
            .frame(maxWidth: 720, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
            .frame(maxWidth: .infinity)
        }
        .refreshable { await load() }
    }

    private func header(_ detail: AppDetail) -> some View {
        HStack(alignment: .top, spacing: 16) {
            RemoteImage(url: detail.iconURL ?? item.iconURL, fallback: "app.fill")
                .frame(width: 76, height: 76)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            VStack(alignment: .leading, spacing: 5) {
                Text(detail.name).font(.title2.weight(.semibold))
                if let developer = detail.developer {
                    Text(developer).font(.subheadline).foregroundStyle(.secondary)
                }
                if let package = detail.packageName ?? item.packageName {
                    Text(package).font(.caption).foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func metadata(_ detail: AppDetail) -> some View {
        VStack(spacing: 12) {
            if let version = detail.version ?? item.version { metadataRow("版本", version) }
            if let size = detail.size { metadataRow("大小", size) }
            if let updatedAt = detail.updatedAt { metadataRow("更新", displayDate(updatedAt)) }
            if let score = detail.score ?? item.score { metadataRow("评分", score) }
            if let downloads = detail.downloads ?? item.downloads { metadataRow("下载", downloads) }
        }
    }

    private func metadataRow(_ title: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 16) {
            Text(title).foregroundStyle(.secondary).frame(width: 42, alignment: .leading)
            Text(value).frame(maxWidth: .infinity, alignment: .leading)
                .textSelection(.enabled)
        }
        .font(.subheadline)
    }

    private func textSection(_ title: String, _ content: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.title3.weight(.semibold))
            Text(content).font(.body).textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func externalURL(_ detail: AppDetail) -> URL? {
        guard let package = detail.packageName ?? item.packageName else { return nil }
        return URL(string: "https://www.coolapk.com/apk/\(package)")
    }

    private func displayDate(_ raw: String) -> String {
        guard let seconds = TimeInterval(raw), seconds > 1_000_000_000 else { return raw }
        let date = Date(timeIntervalSince1970: seconds > 100_000_000_000 ? seconds / 1_000 : seconds)
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        return formatter.string(from: date)
    }

    private func load() async {
        loading = true
        error = nil
        do {
            detail = try await service.appDetail(item.packageName ?? item.id)
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}
