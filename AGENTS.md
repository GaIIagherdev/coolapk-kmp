# AGENTS.md

本文件是给 AI 编码代理（Claude Code 等）的项目基准。修改前先读完；与用户的当次明确指示冲突时，以用户为准，并在回复中说明。

## 项目概况

非官方酷安客户端实验工程。

- `androidApp/`：Android Compose 界面、导航、平台交互。
- `shared/`：KMP 领域模型、解析器、Repository、Gateway。`commonMain` 放解析与模型，`androidMain` 放 Rust 绑定与 Gateway 实现。
- `rust-core/`：酷安 API 核心与 UniFFI 边界（`src/coolapk/client.rs` 是网络与数据逻辑，`src/coolapk/ffi.rs` 是导出给 Kotlin/Swift 的接口）。
- `iosApp/`：SwiftUI 客户端，直接调用 UniFFI Swift 绑定。
- `docs/`：功能矩阵、架构说明、设计稿（`docs/design/`）。

## 界面基准：Material 3 Expressive（M3E）

Android 界面必须遵循 M3E，这是不可妥协的基准。

- 主题入口是 `MaterialExpressiveTheme`，动效使用 `MotionScheme.expressive()`，形状与字体定义在 `ui/theme/Theme.kt`、`ui/theme/Type.kt`。新增颜色先加到 `ui/theme/Color.kt` 的语义令牌，不要在页面里硬编码色值（全屏图片查看器的 `Viewer*` 令牌除外）。
- 颜色必须使用语义角色（`primary`、`surfaceContainer*`、`outlineVariant`、`errorContainer` 等）并兼容亮色与暗色。状态不能只靠降低透明度表达。
- 形状层级：小控件 8–12dp，普通卡片 16–20dp，主容器 24–28dp，按钮与筛选容器用胶囊。紧邻的分组列表用首尾大圆角、中间小圆角的分段容器（`GroupedSurface` / `groupedItemShape`）。
- 间距以 16dp 为基础，区块间距 20–24dp，触控目标至少 48dp（`heightIn(min = 48.dp)`）。
- 加载用 `ExpressiveLoadingIndicator`，空态和错误态用 `CoolapkEmptyState` / `CoolapkErrorState`，不要散落裸文字。
- 区块标题用 `SectionHeader`，统计用 `StatColumn` / `InfoPill`，卡片用 `CoolapkCard`。已有共享组件能满足时不要重复造轮子。
- 动效服务于交互：选中指示器平滑移动，按压有轻微反馈。不要加持续弹跳、大幅视差或装饰性无限动画。
- 图标使用 Material 圆润风格（`Icons.Rounded.*`），不用 emoji 充当产品图标。应用身份用原创图形或文字，不使用酷安官方 Logo。
- 宽屏（≥840dp）：导航切换为 `NavigationRail`（宽度显式设为 `NavigationRailWidth`），内容区用 `widthIn(max = …)` 限宽，图文列表可切为两列。
- 文案使用简体中文，示例数据用真实语气的中文数码社区内容。
- 尊重系统的减少动态效果偏好。

## 功能参考

参考实现为独立仓库 `coolapk-desktop`（Vue/Tauri，只读，不要修改），请在本机自行指定其路径，不要把本机路径写入仓库。

- 参考它的**功能范围、信息结构、数据字段和交互流程**。
- **视觉样式以 M3E 为准**，不照搬桌面端的布局、圆角、颜色或组件形态。
- 参考时先确认 Android 端现有数据是否已经能支撑该功能；不够时在 `rust-core` 补接口，而不是在界面里伪造数据。
- **长期目标**：Android 端功能逐步对齐参考项目，最终覆盖其全部功能。当前只做 Android，iOS 与 KMP `iosMain` 暂不随之改动。
- 进度与待办以 `docs/feature-parity.md` 为准。每完成一个功能（含测试与截图验证）就更新该文件中的状态，不要只在提交信息里记录。
- 开始一项功能前，先读参考项目对应的页面与类型定义，确认字段与交互，再写界面和解析器。

## 数据与接口

- Rust 核心是接口的唯一来源。新增接口的流程：
  1. 在 `rust-core/src/coolapk/client.rs` 实现或确认方法；
  2. 在 `ffi.rs` 的 `#[uniffi::export]` 实现块中加导出包装，返回 JSON 字符串；
  3. 运行 `./scripts/generate-uniffi-bindings.sh` 重新生成 Kotlin 绑定，并去掉生成文件的行尾空白（`sed -i '' 's/[[:space:]]*$//'`），避免无关 diff；
  4. 在 `CoolapkGateway` 中加默认实现（`error(...)` 或 `Result.failure(...)`），再在 `RustCoolapkGateway` 中覆盖；
  5. 在 `shared/src/commonMain` 写解析器，并在 `commonTest` 加测试。
- 解析器必须对缺失字段宽容（`?:` 与 `takeIf`），不因单条数据异常丢掉整页。
- 写操作（想要、关注、发布点评等）失败时要回滚乐观更新并提示用户。
- 没有真实接口样本时，字段依据参考项目的类型定义推断，并在测试或说明里标注"未经真实样本验证"。
- 未登录或需要验证码的接口，界面要给出明确提示，不要静默失败。

## 验证

每次改动后至少执行：

```bash
./gradlew :shared:allTests :androidApp:testDebugUnitTest --offline
cargo test --manifest-path rust-core/Cargo.toml --all-targets
```

涉及界面时，再执行 `./gradlew :androidApp:installDebug --offline`，用 `adb exec-out screencap -p` 截图检查。注意：

- 截图保存到 scratchpad 目录，不要放进仓库。
- 点击坐标以原始像素为准（截图显示尺寸的 1.5 倍），先确认坐标换算再点击。
- 应用刚启动时可能截到黑屏过渡帧，等待几秒后重新截图。
- 如果截图中出现酷安的人机验证弹层，关闭它即可，不要尝试绕过。

## 工作约定

- 工作区里可能有未提交的改动，不要覆盖、回退或格式化与当前任务无关的文件。
- 每完成一项改动（且相关测试通过）就及时提交（commit），不要攒到最后。提交信息沿用仓库现有风格（如 `feat(android): ...`、`feat(shared,rust): ...`），正文可用中文说明。
- 只暂存本次改动涉及的文件（`git add <具体路径>`），不要用 `git add -A`，不要提交与当前任务无关的文件或截图、日志等产物。
- 推送（push）仍需用户明确要求。
- 仓库文件与提交信息中不要出现本机信息：绝对路径（如用户主目录）、设备序列号、模拟器标识、账号、Cookie、真实用户 UID 等。需要引用时用相对路径或描述性说法。
- 写入界面文字前确认与现有文案风格一致，避免出现模拟器或开发者用语。
- 完成后说明：改了什么、哪些已验证、哪些未验证（例如没有真实账号的接口）。
