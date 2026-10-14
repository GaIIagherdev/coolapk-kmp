import SwiftUI
import WebKit

@MainActor
final class OfficialLoginSession: NSObject, ObservableObject {
    @Published private(set) var isLoading = false
    @Published private(set) var isVerifying = false
    @Published private(set) var error: String?
    @Published private(set) var authenticated = false
    @Published private(set) var pageHost = "account.coolapk.com"
    @Published private(set) var isSecurePage = true
    @Published private(set) var isCoolapkPage = true

    let webView: WKWebView

    private let service: CoolapkService
    private var urlObservation: NSKeyValueObservation?
    private var latestURL: URL?
    private var callbackURL: URL?
    private var automaticAttemptKey: Int?
    private var automaticAttempts = 0
    private var nextAutomaticRetry = Date.distantPast
    private var pendingVerification: Task<Void, Never>?
    private var cookiePoll: Task<Void, Never>?
    private var started = false
    private var active = false
    private var cancelled = false

    init(service: CoolapkService) {
        self.service = service
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.configuration.websiteDataStore.httpCookieStore.add(self)
        webView.scrollView.contentInsetAdjustmentBehavior = .automatic
        urlObservation = webView.observe(\.url, options: [.new]) { [weak self] webView, _ in
            guard let url = webView.url else { return }
            Task { @MainActor [weak self] in self?.inspect(url) }
        }
    }

    func start() {
        active = true
        guard !started else { return }
        started = true
        loadLoginPage()
        cookiePoll = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(2))
                guard !Task.isCancelled, let self, !self.cancelled else { return }
                guard let url = self.latestURL,
                      Self.isOfficialLoginPage(url) || Self.returnKind(for: url) != .none else { continue }
                await self.verify(automatic: true)
            }
        }
    }

    func setActive(_ active: Bool) {
        if active {
            if !started {
                start()
            } else {
                self.active = true
                latestURL = nil
                callbackURL = nil
                automaticAttemptKey = nil
                automaticAttempts = 0
                nextAutomaticRetry = .distantPast
                loadLoginPage()
            }
        } else {
            self.active = false
            pendingVerification?.cancel()
            webView.stopLoading()
        }
    }

    func reload() {
        callbackURL = nil
        automaticAttemptKey = nil
        automaticAttempts = 0
        nextAutomaticRetry = .distantPast
        loadLoginPage()
    }

    func completeManually() {
        guard active else { return }
        pendingVerification?.cancel()
        Task { await verify(automatic: false) }
    }

    func cancel() {
        cancelled = true
        active = false
        pendingVerification?.cancel()
        cookiePoll?.cancel()
        webView.configuration.websiteDataStore.httpCookieStore.remove(self)
        webView.stopLoading()
    }

    private func loadLoginPage() {
        error = nil
        isLoading = true
        var callback = URLComponents()
        callback.scheme = "https"
        callback.host = "www.coolapk.com"
        callback.path = "/"
        callback.fragment = "/auth_callback"

        var login = URLComponents()
        login.scheme = "https"
        login.host = "account.coolapk.com"
        login.path = "/auth/login"
        login.queryItems = [
            URLQueryItem(name: "type", value: "coolapk"),
            URLQueryItem(name: "forward", value: callback.url?.absoluteString),
        ]
        guard let url = login.url else {
            isLoading = false
            error = "无法打开酷安官方登录页"
            return
        }
        webView.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData))
    }

    private func inspect(_ url: URL) {
        guard !cancelled, active else { return }
        latestURL = url
        pageHost = url.host ?? url.scheme ?? "未知页面"
        isSecurePage = url.scheme?.lowercased() == "https"
        isCoolapkPage = Self.priority(pageHost) > 0
        switch Self.returnKind(for: url) {
        case .callback:
            callbackURL = url
            scheduleVerification()
        case .landing:
            scheduleVerification()
        case .none:
            break
        }
    }

    private func scheduleVerification() {
        guard active, !authenticated, !isVerifying else { return }
        pendingVerification?.cancel()
        pendingVerification = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(350))
            guard !Task.isCancelled else { return }
            await self?.verify(automatic: true)
        }
    }

    private func verify(automatic: Bool) async {
        guard active, !cancelled, !authenticated, !isVerifying else { return }
        guard let latestURL,
              Self.returnKind(for: latestURL) != .none || Self.isOfficialLoginPage(latestURL) else {
            if !automatic { error = "请在酷安官方登录页面完成登录" }
            return
        }
        isVerifying = true
        defer { isVerifying = false }
        if !automatic { error = nil }

        for attempt in 0..<3 {
            let callback = callbackURL.flatMap(Self.callbackParameters)
            let nativeCookies = await readNativeCookies()
            guard active, !cancelled, !Task.isCancelled else { return }
            let cookie = Self.mergeCookies(callback?.cookie, nativeCookies)
            if Self.hasSession(cookie) {
                let code = callback?.accessCode
                let attemptKey = "\(cookie)|\(code ?? "")".hashValue
                if automatic {
                    if automaticAttemptKey != attemptKey {
                        automaticAttemptKey = attemptKey
                        automaticAttempts = 0
                        nextAutomaticRetry = .distantPast
                    }
                    if automaticAttempts >= 4 {
                        guard Date() >= nextAutomaticRetry else { return }
                        automaticAttempts = 0
                    }
                    automaticAttempts += 1
                    if automaticAttempts == 4 {
                        nextAutomaticRetry = Date().addingTimeInterval(10)
                    }
                }
                do {
                    try await service.login(accessCode: code, cookie: cookie)
                    guard active, !cancelled else { return }
                    authenticated = true
                    error = nil
                } catch {
                    guard active, !cancelled else { return }
                    self.error = "登录校验未通过，请在网页完成登录后重试"
                }
                return
            }
            if attempt < 2 {
                try? await Task.sleep(for: .milliseconds(400))
                guard active, !cancelled, !Task.isCancelled else { return }
            }
        }
        if !automatic { error = "暂未获取到酷安会话，请完成网页登录后重试" }
    }

    private func readNativeCookies() async -> String {
        let cookies = await withCheckedContinuation { continuation in
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies {
                continuation.resume(returning: $0)
            }
        }
        var values: [String: String] = [:]
        for cookie in cookies.sorted(by: { Self.priority($0.domain) < Self.priority($1.domain) }) {
            guard Self.priority(cookie.domain) > 0,
                  cookie.expiresDate.map({ $0 > Date() }) ?? true,
                  !cookie.name.isEmpty,
                  !cookie.name.contains(";") else { continue }
            if cookie.name.caseInsensitiveCompare("SESSID") == .orderedSame,
               !Self.validSessionValue(cookie.value) { continue }
            values[cookie.name] = cookie.value
        }
        return values.keys.sorted().map { "\($0)=\(values[$0] ?? "")" }.joined(separator: "; ")
    }

    private static func priority(_ domain: String) -> Int {
        let domain = domain.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
        guard domain == "coolapk.com" || domain.hasSuffix(".coolapk.com") else { return 0 }
        if domain == "account.coolapk.com" { return 3 }
        if domain == "coolapk.com" { return 2 }
        return 1
    }

    private static func validSessionValue(_ value: String) -> Bool {
        let value = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return !value.isEmpty && !["deleted", "expired", "null", "undefined"].contains(value)
    }

    private static func hasSession(_ header: String) -> Bool {
        header.split(separator: ";").contains { pair in
            let parts = pair.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            return parts.count == 2 && parts[0].caseInsensitiveCompare("SESSID") == .orderedSame
                && validSessionValue(parts[1])
        }
    }

    private static func mergeCookies(_ callback: String?, _ native: String) -> String {
        var values: [String: String] = [:]
        for header in [callback ?? "", native] {
            for pair in header.split(separator: ";") {
                let parts = pair.split(separator: "=", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
                guard parts.count == 2, !parts[0].isEmpty, !parts[0].contains(" ") else { continue }
                values[parts[0]] = parts[1]
            }
        }
        return values.keys.sorted().map { "\($0)=\(values[$0] ?? "")" }.joined(separator: "; ")
    }

    private struct CallbackParameters {
        let accessCode: String?
        let cookie: String?
    }

    private static func callbackParameters(_ url: URL) -> CallbackParameters? {
        guard returnKind(for: url) == .callback,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        let queryItems: [URLQueryItem]
        if let fragment = components.percentEncodedFragment,
           fragment.hasPrefix("/auth_callback"),
           let fragmentURL = URLComponents(string: "https://www.coolapk.com\(fragment)") {
            queryItems = fragmentURL.queryItems ?? []
        } else {
            queryItems = components.queryItems ?? []
        }
        let code = queryItems.first { $0.name == "code" }?.value
        let accessCode = queryItems.first { $0.name == "ac" }?.value == "access_token"
            ? code?.trimmingCharacters(in: .whitespacesAndNewlines) : nil
        return CallbackParameters(
            accessCode: accessCode?.isEmpty == false ? accessCode : nil,
            cookie: queryItems.first { $0.name == "ck" }?.value
        )
    }

    private enum ReturnKind { case callback, landing, none }

    private static func returnKind(for url: URL) -> ReturnKind {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.scheme?.lowercased() == "https",
              components.user == nil, components.password == nil,
              components.port == nil || components.port == 443 else { return .none }
        let host = components.host?.lowercased()
        if host == "account.coolapk.com", components.path == "/auth/callback" { return .callback }
        guard host == "www.coolapk.com", components.path == "/" || components.path.isEmpty else { return .none }
        if let fragment = components.percentEncodedFragment, !fragment.isEmpty {
            return fragment.split(separator: "?", maxSplits: 1).first == "/auth_callback" ? .callback : .none
        }
        return .landing
    }

    private static func isOfficialLoginPage(_ url: URL) -> Bool {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return false }
        return components.scheme?.lowercased() == "https"
            && components.host?.lowercased() == "account.coolapk.com"
            && components.path == "/auth/login"
            && components.user == nil && components.password == nil
            && (components.port == nil || components.port == 443)
    }
}

extension OfficialLoginSession: WKNavigationDelegate, WKUIDelegate {
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        isLoading = true
        error = nil
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        isLoading = false
        if let url = webView.url { inspect(url) }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        isLoading = false
        self.error = "网页加载失败，请检查网络后重试"
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        isLoading = false
        self.error = "网页加载失败，请检查网络后重试"
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let url = navigationAction.request.url {
            let scheme = url.scheme?.lowercased()
            guard scheme == "https" || url.absoluteString == "about:blank" else {
                error = "此登录链接无法在应用内打开"
                decisionHandler(.cancel)
                return
            }
            if navigationAction.targetFrame?.isMainFrame != false { inspect(url) }
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.targetFrame == nil { webView.load(navigationAction.request) }
        return nil
    }
}

extension OfficialLoginSession: WKHTTPCookieStoreObserver {
    func cookiesDidChange(in cookieStore: WKHTTPCookieStore) {
        guard let latestURL,
              Self.isOfficialLoginPage(latestURL) || Self.returnKind(for: latestURL) != .none else { return }
        scheduleVerification()
    }
}

struct OfficialLoginWebView: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> WKWebView { webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
