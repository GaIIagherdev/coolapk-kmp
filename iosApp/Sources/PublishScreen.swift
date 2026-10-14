import SwiftUI

struct PublishScreen: View {
    let service: CoolapkService

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    @State private var message = ""
    @State private var preview = false
    @State private var accountKey = "guest"
    @State private var loggedIn = false
    @State private var loading = true
    @State private var submitting = false
    @State private var error: String?
    @State private var notice: String?

    private let maxLength = 1000

    private var draftKey: String { "coolapk.publish.draft.\(accountKey)" }
    private var hasContent: Bool { !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var canPublish: Bool {
        loggedIn && hasContent && message.count <= maxLength && !submitting
    }
    private var accountStatus: String {
        if !loggedIn { return "未登录，登录后才能发布动态" }
        return "已登录 · " + accountKey.replacingOccurrences(of: "uid:", with: "")
    }
    private var draftBinding: Binding<String> {
        Binding(
            get: { message },
            set: { newValue in
                message = String(newValue.prefix(maxLength))
                saveDraft()
            }
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            modePicker
            editorContent
            actionBar
        }
        .background(CoolapkStyle.canvas.opacity(0.65))
        .navigationTitle("发布动态")
        .overlay { if loading { ProgressView().controlSize(.large) } }
        .alert("提示", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
            Button("好的", role: .cancel) {}
        } message: { Text(notice ?? "") }
        .onAppear { Task { await loadDraft() } }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active && hasContent { saveDraft() }
        }
    }

    private var modePicker: some View {
        Picker("模式", selection: $preview) {
            Text("编辑").tag(false)
            Text("预览").tag(true)
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var editorContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if preview {
                    previewCard
                } else {
                    TextField("分享这一刻的酷搞感受…", text: draftBinding, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(8...16)
                        .disabled(submitting)
                }
                HStack {
                    Text("\(message.count)/\(maxLength)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Spacer()
                    if !message.isEmpty {
                        Button("清除") { message = ""; saveDraft() }
                            .buttonStyle(.bordered)
                            .disabled(submitting)
                    }
                }
                accountCard
                if let error { errorCard(error) }
            }
            .frame(maxWidth: 760, alignment: .leading)
            .padding(16)
        }
    }

    private var accountCard: some View {
        HStack(spacing: 10) {
            Image(systemName: loggedIn ? "checkmark.seal.fill" : "person.crop.circle.badge.xmark")
                .foregroundStyle(loggedIn ? CoolapkStyle.green : Color.red)
            Text(accountStatus).font(.subheadline)
            Spacer()
        }
        .padding(14)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private func errorCard(_ value: String) -> some View {
        Text(value)
            .font(.subheadline)
            .foregroundStyle(.red)
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.red.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private var actionBar: some View {
        if #available(iOS 26, *) {
            GlassEffectContainer(spacing: 12) {
                actionButtons
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.bar)
        } else {
            actionButtons
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(.bar)
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 12) {
            Button {
                saveDraft()
                notice = "草稿已保存"
            } label: {
                Label("保存草稿", systemImage: "square.and.arrow.down")
            }
            .modifier(CoolapkActionButtonStyle())
            .disabled(loading || submitting)
            Spacer()
            Button {
                Task { await publish() }
            } label: {
                Label(submitting ? "发布中" : "发布", systemImage: "paperplane.fill")
            }
            .modifier(CoolapkActionButtonStyle(prominent: true))
            .tint(colorScheme == .dark ? CoolapkStyle.darkGreen : CoolapkStyle.green)
            .disabled(!canPublish)
        }
    }

    private var previewCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("预览", systemImage: "eye")
                .font(.headline)
            Divider()
            Text(hasContent ? message : "输入内容后这里会显示动态预览")
                .foregroundStyle(hasContent ? .primary : .secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private func loadDraft() async {
        let currentAccount = await service.accountKey()
        if currentAccount != accountKey && !loading { saveDraft() }
        let accountChanged = currentAccount != accountKey
        accountKey = currentAccount
        loggedIn = await service.isLoggedIn()
        if accountChanged || loading {
            message = UserDefaults.standard.string(forKey: draftKey) ?? ""
        }
        loading = false
    }

    private func saveDraft() {
        guard !accountKey.isEmpty else { return }
        UserDefaults.standard.set(message, forKey: draftKey)
    }

    private func publish() async {
        guard canPublish else {
            error = loggedIn ? "请输入动态内容" : "请先登录后再发布"
            return
        }
        submitting = true
        error = nil
        let before = accountKey
        let currentKey = await service.accountKey()
        guard before == currentKey, await service.isLoggedIn() else {
            submitting = false
            loggedIn = false
            error = "登录状态已变化，请重新确认后再发布"
            return
        }
        do {
            _ = try await service.createFeed(message: message)
            UserDefaults.standard.removeObject(forKey: draftKey)
            message = ""
            notice = "发布成功"
        } catch {
            self.error = publishError(error)
        }
        submitting = false
    }

    private func publishError(_ error: Error) -> String {
        let raw = error.localizedDescription
        if raw.localizedCaseInsensitiveContains("captcha") || raw.contains("验证码") {
            return "发布需要通过酷安验证或登录官方客户端后重试"
        }
        if raw.contains("风控") || raw.localizedCaseInsensitiveContains("device") {
            return "酷安服务端暂时拒绝此设备发布，请稍后重试"
        }
        return raw.isEmpty ? "发布失败，请稍后重试" : raw
    }
}
