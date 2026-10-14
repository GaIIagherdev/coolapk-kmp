# 已知问题

## 人机验证（网易易盾）覆盖不全

酷安会在风控触发时对接口返回 `403 err_request_captcha_v2`。桌面端 `coolapk-desktop` 的处理是**三层兜底**：

1. 正常请求接口；
2. 命中验证码时弹出易盾滑块，拿到 `validate` 后拼成 `NEC:{captchaId前8位}:{validate}`，带 `_v2_post_token` 重试一次；
3. 仍失败或用户取消，则改走 `https://www.coolapk.com/feed/{id}`；该入口带 `X-Requested-With: XMLHttpRequest` + `X-App-Id: com.coolapk.market` 时**直接返回 JSON**，绕过 `api.coolapk.com` 的风控。

本轮只把**动态详情**这条链路补齐（第 2、3 层），其余入口仍是"检测到验证码就显示一句文案"，属于已知缺口。

| 入口 | 状态 | 说明 |
| --- | --- | --- |
| 动态详情 | 已实现 | 滑块验证 + 令牌重试 + 网页版 JSON 兜底 |
| 发表动态 | **未实现** | `PublishViewModel.kt` 仅把 captcha 错误翻译成文案 |
| 回复某条评论 | **未实现** | 与桌面端的 `FeedCommentSection.vue` 对应能力缺失 |
| 直接发表评论 | **未实现** | 同上 |
| 编辑动态 | **未实现** | 复用发布链路，同样缺验证码重试 |
| iOS 端全部入口 | **未实现** | iOS 未接 KMP Gateway，需在 Swift 侧单独补 |

写操作（发布/评论/回复）**没有只读兜底**，必须真正完成滑块验证，所以不能只靠网页版 JSON 兜底，需要复用同一套易盾弹层。

### 复用点

- 纯逻辑（验证码参数解析、令牌拼接、网页版 URL 与信封提取）：`shared/src/commonMain/kotlin/com/coolapk/kmp/core/FeedDetailFallback.kt`，已有单元测试。
- 易盾弹层：`androidApp/src/main/kotlin/app/coolapk/kmp/ui/CaptchaDialog.kt` + `androidApp/src/main/assets/ne_captcha.html`，契约是 `CaptchaDialog(captchaId, onVerified, onDismissed)`。
- Rust 侧已导出 `get_feed_detail_with_token` 与 `fetch_external_page`（`rust-core/src/coolapk/ffi.rs`）；发布/评论需要类似的"带令牌重试"导出，因为现有 `create_feed`/`reply_feed` 的 FFI 签名同样没有令牌参数。

### 待真机验证

- 易盾 WebView 方案依赖页面 origin。当前用 `loadDataWithBaseURL("https://www.coolapk.com/", ...)` 伪造 origin，**尚未在真机上确认易盾 SDK 会正常下发验证资源**；若 SDK 校验真实域名或 Referer，需要改用网易易盾 Android 原生 SDK。
- 网页版 JSON 兜底依赖"带 XHR 指纹头即返回 JSON"这一服务端行为，来自桌面端注释里的实测结论，**当前环境未复现验证**。
- 令牌重试只重试一次；连续触发风控的账号可能仍失败。

## 文档滞后

`README.md`、`docs/feature-matrix.md` 与 `docs/design/android-m3e-brief.md` 仍写着"用户和话题搜索结果没有完整详情页"，但对应页面已经实现并入库。以代码为准。
