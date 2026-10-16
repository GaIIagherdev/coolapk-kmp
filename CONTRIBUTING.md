# 贡献指南

感谢你关注这个非官方酷安客户端实验工程。欢迎提交 Issue 和 Pull Request，尤其是下面这些方向：

- **功能对齐**：参照参考项目 `coolapk-desktop` 的功能范围，补齐 `docs/feature-parity.md` 中未开始的功能。
- **界面打磨**：按 Material 3 Expressive 规范完善 Android 界面。
- **接口解析**：补充解析器、测试样本，或修正字段兼容问题。

## 开始之前

- 先读 [`AGENTS.md`](AGENTS.md)：它是项目的设计、数据和工程约定。
- 功能进度以 [`docs/feature-parity.md`](docs/feature-parity.md) 为准，认领前可以先看一眼对应条目。
- 与用户可见的文案保持简体中文，示例数据使用真实语气的中文数码社区内容。

## 本地构建与检查

```bash
./gradlew :shared:allTests :androidApp:testDebugUnitTest --offline
cargo test --manifest-path rust-core/Cargo.toml --all-targets
```

涉及界面的改动，请附上真机或模拟器的截图说明（截图不要提交到仓库，展示用途的除外）。

## 设计要求（Android）

- 界面遵循 Material 3 Expressive：使用语义颜色角色，不在页面里硬编码色值。
- 加载、空态、错误态分别使用 `ExpressiveLoadingIndicator`、`CoolapkEmptyState`、`CoolapkErrorState`。
- 触控目标不小于 48dp，分组列表使用 `GroupedSurface` / `groupedItemShape`。
- 减少动态效果的系统设置需要被尊重。

## 接口与解析

- Rust 核心是接口的唯一来源，新增接口按 `AGENTS.md` 的五步流程完成。
- 解析器对缺失字段保持宽容，单条数据异常不应导致整页丢失。
- 没有真实样本的字段，请在测试或说明中标注“未经真实样本验证”。

## 提交规范

提交信息沿用仓库风格，例如：

- `feat(android): 新增收藏页`
- `feat(shared,rust): 补充黑名单接口`
- `fix(shared): 修正图片地址协议`
- `docs: 更新功能矩阵`

正文可以使用中文说明动机和影响。每次提交只包含与本次改动相关的文件。

## 隐私

提交内容中不要包含个人账号信息，包括昵称、UID、Cookie、Token、设备序列号、本机绝对路径等。截图和日志在提交前请先检查并脱敏。

## 问题与讨论

- 功能缺口或 Bug：请使用 Issue，并说明复现步骤、期望行为和设备信息。
- 接口相关的问题：如果你有脱敏的接口响应样本，请一并附上，这是最有价值的帮助。
