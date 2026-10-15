package app.coolapk.kmp.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val LOGIN_CALLBACK_URL = "https://www.coolapk.com/#/auth_callback"
private val LOGIN_COOKIE_URLS = listOf(
    "https://api.coolapk.com/",
    "https://m.coolapk.com/",
    "https://www.coolapk.com/",
    "https://account.coolapk.com/",
)

internal data class OfficialLoginCallback(val accessCode: String?, val cookie: String?)

internal fun officialLoginUrl(): String = Uri.Builder()
    .scheme("https")
    .authority("account.coolapk.com")
    .path("/auth/login")
    .appendQueryParameter("type", "coolapk")
    .appendQueryParameter("forward", LOGIN_CALLBACK_URL)
    .build()
    .toString()

internal fun parseOfficialLoginCallback(url: String): OfficialLoginCallback? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme != "https") return null
    val host = uri.host?.lowercase(Locale.ROOT)
    val officialCallback = host == "account.coolapk.com" && uri.path == "/auth/callback"
    val forwardCallback = host == "www.coolapk.com" && uri.path == "/" &&
        uri.fragment?.substringBefore('?') == "/auth_callback"
    if (!officialCallback && !forwardCallback) return null
    val parameters = sequenceOf(uri.rawQuery, uri.rawFragment?.substringAfter('?', ""))
        .filterNotNull()
        .flatMap { it.split('&').asSequence() }
        .mapNotNull { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (key.isEmpty()) null else decodeLoginParam(key) to decodeLoginParam(value)
        }
        .toMap()
    val code = parameters["code"]?.takeIf(String::isNotBlank)
        ?.takeIf { parameters["ac"] == "access_token" }
    return OfficialLoginCallback(code, parameters["ck"]?.takeIf(String::isNotBlank))
}

private fun decodeLoginParam(value: String): String =
    runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault("")

internal fun mergeLoginCookies(headers: List<String?>): String {
    val cookies = linkedMapOf<String, Pair<String, String>>()
    for (header in headers) {
        for (entry in header.orEmpty().split(';')) {
            val name = entry.substringBefore('=').trim()
            val value = entry.substringAfter('=', "").trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                cookies[name.lowercase(Locale.ROOT)] = name to value
            }
        }
    }
    return cookies.values.joinToString("; ") { (name, value) -> "$name=$value" }
}

internal fun hasLoginSession(cookie: String): Boolean = cookie.split(';').any { entry ->
    val name = entry.substringBefore('=').trim()
    val value = entry.substringAfter('=', "").trim()
    name == "SESSID" && value.isNotEmpty() &&
        !value.equals("deleted", ignoreCase = true) && !value.equals("expired", ignoreCase = true)
}

private fun readLoginCookies(callbackCookie: String?): String {
    val manager = CookieManager.getInstance()
    return mergeLoginCookies(listOf(callbackCookie) + LOGIN_COOKIE_URLS.map(manager::getCookie))
}

private fun isOfficialLanding(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" && uri.host?.lowercase(Locale.ROOT) in
        setOf("coolapk.com", "www.coolapk.com", "m.coolapk.com")
}

private fun isOfficialLoginPage(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme == "https" && uri.host?.lowercase(Locale.ROOT) == "account.coolapk.com" &&
        uri.path == "/auth/login"
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfficialLoginScreen(
    onDismiss: () -> Unit,
    onManualImport: () -> Unit,
    onCredentials: (cookie: String, accessCode: String?, onResult: (String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val active = remember { AtomicBoolean(true) }
    val currentOnCredentials by rememberUpdatedState(onCredentials)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var callback by remember { mutableStateOf<OfficialLoginCallback?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var verifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentHost by remember { mutableStateOf("account.coolapk.com") }

    fun verify(manual: Boolean) {
        if (verifying || !active.get()) return
        val cookie = readLoginCookies(callback?.cookie)
        if (!hasLoginSession(cookie)) {
            if (manual) error = "尚未取得登录会话，请先在网页完成登录后重试"
            return
        }
        verifying = true
        error = null
        currentOnCredentials(cookie, callback?.accessCode) { result ->
            if (!active.get()) return@currentOnCredentials
            verifying = false
            if (result == null) {
                onDismiss()
            } else {
                callback = callback?.copy(accessCode = null)
                if (manual) error = result
            }
        }
    }

    LaunchedEffect(Unit) {
        var lastCookieFingerprint: Int? = null
        var attempts = 0
        var lastAttemptAt = 0L
        while (active.get()) {
            delay(500)
            if (!active.get()) break
            val url = webView?.url ?: continue
            if (parseOfficialLoginCallback(url) == null &&
                !isOfficialLanding(url) && !isOfficialLoginPage(url)
            ) continue
            val cookie = readLoginCookies(callback?.cookie)
            if (!hasLoginSession(cookie)) continue
            val fingerprint = cookie.hashCode()
            if (fingerprint != lastCookieFingerprint) {
                lastCookieFingerprint = fingerprint
                attempts = 0
                lastAttemptAt = 0L
            }
            if (verifying || attempts >= 4 || SystemClock.elapsedRealtime() - lastAttemptAt < 2_000L) {
                continue
            }
            attempts++
            lastAttemptAt = SystemClock.elapsedRealtime()
            verify(manual = false)
        }
    }

    BackHandler { if (!verifying) onDismiss() }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("酷安官方登录") },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !verifying) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭登录")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        error = null
                        webView?.reload()
                    }, enabled = !verifying) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新登录页")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Button(
                        onClick = { verify(manual = true) },
                        enabled = !verifying,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (verifying) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp).size(18.dp),
                            )
                        }
                        Text(if (verifying) "验证登录中" else "完成登录")
                    }
                    TextButton(
                        onClick = onManualImport,
                        enabled = !verifying,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("改用 Cookie 导入") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (pageLoading) {
                LinearWavyProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            AndroidView(
                factory = {
                    WebView(context).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                if (request.url.scheme != "https") return true
                                parseOfficialLoginCallback(request.url.toString())?.let {
                                    callback = it
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                pageLoading = true
                                currentHost = runCatching { URI(url).host }.getOrNull() ?: currentHost
                                parseOfficialLoginCallback(url)?.let {
                                    callback = it
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                pageLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                webError: WebResourceError,
                            ) {
                                if (request.isForMainFrame) {
                                    pageLoading = false
                                    error = "登录页面无法加载，请检查网络后刷新"
                                }
                            }
                        }
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            removeAllCookies {
                                if (active.get()) {
                                    loadUrl(officialLoginUrl())
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                onRelease = { view ->
                    active.set(false)
                    view.stopLoading()
                    view.destroy()
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    currentHost,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
