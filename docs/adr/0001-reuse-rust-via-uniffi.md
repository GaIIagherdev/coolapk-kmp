# ADR 0001：通过 UniFFI 复用 Rust API 核心

- 状态：已接受
- 日期：2026-10-12
- 范围：`rust-core`、`shared`、Android 首阶段

## 背景

原 `coolapk-desktop` 已包含一套长期维护的酷安 API 客户端实现，覆盖签名、请求头、Cookie、动态、用户、话题、消息等大量接口。KMP 版本需要优先复用这些已验证的行为，同时保留后续替换为纯 Kotlin/Ktor 或原生实现的边界。

## 决策

1. 将桌面端 `src-tauri/src/coolapk/` 中与 Tauri 无关的 API 核心提取到 `rust-core`。
2. 通过 UniFFI 暴露稳定的 Kotlin API，不在 UI 层直接依赖 JNI 细节。
3. KMP 共享层保留 `CoolapkGateway` 抽象，Android 当前使用 `RustCoolapkGateway`。
4. 首阶段只构建 Android `arm64-v8a`，不打包桌面端更新、托盘、窗口等逻辑。
5. Cookie/Token 等敏感持久化后续交给平台存储；当前 FFI 仅提供内存会话配置。

## 理由

- **行为一致性**：签名、请求头、接口路径和响应处理直接沿用原实现，降低复刻风险。
- **维护成本**：数百个 API 方法无需一次性转写成 Kotlin；KMP 层先聚焦领域模型和 UI。
- **可替换性**：`CoolapkGateway` 将业务层与 UniFFI 隔离，未来可按模块逐步替换。
- **体积可控**：`arm64-v8a` Release `.so` 实测约 **2.4 MB**（`opt-level=z`、LTO、strip）。
- **风险隔离**：桌面端特有功能没有进入移动端，避免无关依赖继续膨胀。

## 代价

- 构建链增加 Rust、NDK、UniFFI 生成步骤。
- Kotlin/Objective-C/Swift 绑定与 Rust ABI 必须同步更新。
- 需要处理跨平台 Cookie/凭据存储，不能直接复用桌面文件路径。
- iOS 仍需单独生成 framework 并接入 Xcode；当前仅保留 KMP 接口占位。

## 备选方案

- **全部转写 Kotlin/Ktor**：初期不可行，接口量大且容易产生细微签名差异。
- **直接 JNI 手写桥接**：样板代码和维护成本高于 UniFFI。
- **继续使用 Tauri 整包**：不符合原生 Android 与 M3E 目标，移动端复用度低。

## 体积验证

在 macOS + Android NDK `27.1.12297006`、Rust `1.95.0`、`aarch64-linux-android` Release 条件下：

- `libcoolapk_core.so`：约 **2.4 MB**
- Debug APK：约 **23 MB**（包含 Compose、Coil、JNA 和调试资源，不代表发布体积）
- AAB/Release 可继续按 ABI 拆分，实际安装包只携带对应架构。

## 后续约束

- Rust 错误字段不要命名为 `message`，UniFFI 生成的 Kotlin 会与 `Throwable.message` 冲突。
- 更新 FFI 导出后，必须重新生成绑定并运行 `cargo test`、共享层测试和 Android 构建。
