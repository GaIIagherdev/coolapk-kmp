# 架构

```text
Android: androidApp (Compose / Coolapk green / Material 3 Expressive)
             -> shared/commonMain (Models / Repository / JSON 解析器)
             -> shared/androidMain (RustCoolapkGateway / 加密会话存储)
             -> UniFFI Kotlin + JNA -> Rust Android .so

iOS:     iosApp (SwiftUI / 原生绿色主题)
             -> CoolapkService / Swift JSON 映射 / Keychain
             -> UniFFI Swift -> Rust XCFramework

共同 API 核心: rust-core (CoolapkClient / Auth / HTTP Session / API 方法)
```

## 层次职责

- `androidApp`：五个主导航入口、搜索、动态详情与评论、应用列表与基础详情、发现栏目、只读通知列表、登录和互动界面。评论入口打开详情时定位评论区；发现卡片可处理站内动态、应用及 `#/` 链接。窄屏使用底部导航，宽屏使用 `NavigationRail` 和双列应用列表。
- `shared/commonMain`：Android 使用的领域模型、Gateway/Repository 接口、首页与搜索、应用、动态详情/评论、发现配置/栏目、资料及通知 JSON 解析器。Rust 返回 JSON，映射和部分分页状态留在 Kotlin 层。
- `shared/androidMain`：将共享层请求适配为 UniFFI Kotlin 调用，在 IO dispatcher 执行；Gateway 负责 Android 会话加密保存和恢复。
- `iosApp`：独立 SwiftUI 界面。`CoolapkService` 直接调用 UniFFI Swift 绑定，Swift 侧解析列表、发现配置/栏目、应用详情、评论、资料与通知 JSON，并用 Keychain 保存会话；未使用 KMP Repository 或 `shared/iosMain` Gateway。
- `rust-core`：复用桌面端 API 核心，处理签名、请求头、Cookie、登录校验和服务端接口。UniFFI 暴露首页、热榜、发现配置/栏目、快讯、应用、动态详情/评论、资料、通知、搜索及互动方法；两端只调用各自界面已接入的子集。
- `scripts`：Rust Android 交叉编译、UniFFI Kotlin 绑定生成，以及 iOS Swift 绑定、模拟器/设备静态库和 XCFramework 构建。Android 构建脚本目前仅支持 `arm64-v8a`。

## 数据流

1. Android ViewModel 经 `HomeRepository` 调用 `CoolapkGateway`。`RustCoolapkGateway` 在 IO dispatcher 上进入 UniFFI，Rust 在 Tokio Runtime 中请求接口并返回 JSON。
2. 共享层解析器将 JSON 转成动态、应用、详情、发现栏目、资料和通知模型；Compose 根据页面状态显示内容、加载中、空结果和错误。首页使用首尾游标，发现栏目额外维护 `pageContext`，其他列表按页加载。
3. Android 动态互动、评论互动与登录经 Gateway 传给 Rust；只在请求成功后更新界面状态。应用详情通过共享层取得原始 JSON 后解析并渲染。
4. iOS `CoolapkService` 直接使用 UniFFI Swift 绑定请求 Rust，并在 Swift 侧将 JSON 映射为列表、发现配置/栏目、应用详情、评论、资料和通知模型。发现页按服务端配置加载栏目，分页携带首尾游标和 `pageContext`，配置不可用时回退科技快讯；站内动态和应用走原生页面，Web 链接交给系统打开。动态正文详情沿用列表数据预览，评论及楼中楼分别请求接口；信息流评论入口定位评论区，直接发表评论成功后刷新列表。通知列表仅用于阅读，含动态目标的通知会另取动态详情。

## 平台边界

- Android：主要列表和上述详情、评论、发现、通知路径已经接入，但未覆盖官方客户端全部卡片、发现栏目、榜单与个人内容；通知列表只读，应用详情不提供原生下载安装。游客访问完整动态详情接口时可能遇到服务端验证码要求。
- iOS：已生成模拟器和设备静态库组成的 XCFramework，SwiftUI 工程可在模拟器及通用 iOS 目标构建。此前在 iPhone 16 Pro iOS 18.6 模拟器验证首页真实动态与图片；本轮新增页面完成构建和纯 Foundation 解析测试，页面交互尚未逐项在模拟器验收，登录和互动缺少真实账号验证。发现页特殊卡片未全面适配，通知只读；游客从通知获取完整动态详情也可能遇验证码。`shared/iosMain` 的 `actual` Gateway 仍为占位，不提供真实数据。
- 桌面：不属于本仓库目标；原 `coolapk-desktop` 仓库保持独立。

## 会话与安全

- Android 提供酷安官方 WebView 登录和手动 Cookie 导入。Rust 校验登录会话并在失败时恢复先前 Cookie；成功后 Gateway 用 Android Keystore 密钥和 AES-GCM 加密保存 Cookie。下次启动解密并恢复到 Rust 客户端；退出登录清除内存会话与本地记录。
- iOS 的 `CoolapkService` 提供酷安官方 WebView 登录、手动 Cookie 导入、验证和 Keychain 保存，启动时尝试恢复，退出时清除；这条账号路径尚未使用真实凭据验收。
- Android Manifest 禁用备份。两端登录页的授权回跳和验证码尚未用真实账号验收；Cookie、Token 和签名材料不应进入日志。
