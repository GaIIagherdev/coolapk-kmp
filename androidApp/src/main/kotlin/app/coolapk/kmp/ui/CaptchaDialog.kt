package app.coolapk.kmp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

private const val CAPTCHA_ASSET_NAME = "ne_captcha.html"
private const val CAPTCHA_BRIDGE_NAME = "AndroidCaptcha"
private const val CAPTCHA_BASE_URL = "https://www.coolapk.com/"
private const val MAX_VALIDATE_LENGTH = 4096

/**
 * 暴露给 WebView 内第三方脚本（网易易盾 load.min.js）的 JS 桥。
 *
 * 安全考量：桥对象只暴露必要方法；被注入的脚本是外部第三方代码，且桥方法运行在 WebView 的
 * JavaBridge 线程上，所以这里把入参一律当作不可信数据（trim、截断长度、空值视为失败），
 * 并且只把结果切回主线程后再交给 Compose 回调。
 */
private class CaptchaJavaScriptBridge(
    private val mainHandler: Handler,
    private val onValidate: (String) -> Unit,
    private val onCancel: () -> Unit,
) {
    @JavascriptInterface
    fun onVerified(validate: String?) {
        val normalized = validate?.trim().orEmpty().take(MAX_VALIDATE_LENGTH)
        mainHandler.post {
            if (normalized.isEmpty()) onCancel() else onValidate(normalized)
        }
    }

    @JavascriptInterface
    fun onCancelled() {
        mainHandler.post { onCancel() }
    }
}

private fun readCaptchaAsset(context: Context): String? = runCatching {
    context.assets.open(CAPTCHA_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
}.getOrNull()

/**
 * 网易易盾滑块人机验证弹层。
 *
 * 内联 HTML 通过 [WebView.loadDataWithBaseURL] 加载，baseUrl 固定为酷安官方域：这样页面 origin
 * 是酷安官方域名，易盾 SDK 才会正常初始化并下发验证资源，同时也不必开启 allowFileAccess
 * （避免 file:///android_asset/ 读取本地资源带来的安全风险）。
 *
 * @param captchaId 易盾业务 ID（一般由服务端 403 响应下发）
 * @param onVerified 仅在滑块验证成功时回调，且最多一次；参数是易盾返回的原始 validate 凭证，
 *   `NEC:` 前缀由调用方自行拼接
 * @param onDismissed 在用户点关闭、脚本加载失败、验证出错或组件被销毁时回调，最多一次
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal fun CaptchaDialog(
    captchaId: String,
    onVerified: (validate: String) -> Unit,
    onDismissed: () -> Unit,
) {
    val context = LocalContext.current
    val settled = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // rememberUpdatedState 返回的 State 实例在重组之间保持稳定，因此下面 remember 出来的桥
    // 即使捕获了首次组合的闭包，读到的也始终是最新的回调。
    val verifiedCallback = rememberUpdatedState(onVerified)
    val dismissedCallback = rememberUpdatedState(onDismissed)
    val currentCaptchaId = rememberUpdatedState(captchaId)
    val html = remember(context) { readCaptchaAsset(context) }
    // 与 WebView 同生命周期（两者都以 context 为 key）。
    val started = remember(context) { AtomicBoolean(false) }
    val released = remember(context) { AtomicBoolean(false) }

    // 成功与关闭互斥：谁先抢到 settled 谁生效，保证 onVerified / onDismissed 不会各调一次或重复调。
    val dismissOnce: () -> Unit = {
        if (settled.compareAndSet(false, true)) dismissedCallback.value()
    }

    val bridge = remember {
        CaptchaJavaScriptBridge(
            mainHandler = mainHandler,
            onValidate = { validate ->
                if (settled.compareAndSet(false, true)) verifiedCallback.value(validate)
            },
            onCancel = {
                if (settled.compareAndSet(false, true)) dismissedCallback.value()
            },
        )
    }

    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            addJavascriptInterface(bridge, CAPTCHA_BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // 只唤醒一次，并且已经结束（成功/取消）时不再启动验证。
                    if (!started.compareAndSet(false, true) || settled.get()) return
                    val id = JSONObject.quote(currentCaptchaId.value)
                    view.evaluateJavascript("window.startCoolapkCaptcha($id);", null)
                }
            }
        }
    }

    // 先摘掉 JS 桥（第三方脚本之后不可能再回调），再释放 WebView。
    // AndroidView 的 onRelease 与 DisposableEffect 都可能触发，用原子标志保证只释放一次。
    fun releaseWebView() {
        if (!released.compareAndSet(false, true)) return
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.removeJavascriptInterface(CAPTCHA_BRIDGE_NAME)
        webView.stopLoading()
        webView.destroy()
    }

    DisposableEffect(webView) {
        onDispose {
            // 组件被销毁时先结束回调，避免迟到的桥消息在销毁后再次触发任何回调。
            dismissOnce()
            releaseWebView()
        }
    }

    val captchaHtml = html
    LaunchedEffect(captchaHtml) {
        if (captchaHtml == null) dismissOnce()
    }
    if (captchaHtml == null) return

    Dialog(
        onDismissRequest = dismissOnce,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "酷安人机验证",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = dismissOnce) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭人机验证")
                    }
                }
                Text(
                    "完成滑块验证后会自动继续加载",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                HorizontalDivider()
                AndroidView(
                    factory = {
                        webView.loadDataWithBaseURL(
                            CAPTCHA_BASE_URL,
                            captchaHtml,
                            "text/html",
                            "utf-8",
                            null,
                        )
                        webView
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onRelease = { releaseWebView() },
                )
            }
        }
    }
}
