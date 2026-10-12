# Coolapk KMP

非官方酷安客户端实验工程。Android UI 使用 Jetpack Compose Material 3 Expressive 方向，共享业务层使用 Kotlin Multiplatform，API 核心通过 UniFFI 复用 Rust 实现。

> 本项目不包含或分发酷安官方 Logo、商标素材及私有签名材料。请遵守目标服务条款并仅用于学习研究。

## 模块

- `androidApp/`：Android Compose 客户端
- `shared/`：KMP 领域模型、Gateway 抽象与仓库
- `rust-core/`：从 `coolapk-desktop` 提取的 API 核心与 UniFFI 边界

## 首阶段验证

```bash
./gradlew :shared:assembleDebug :androidApp:assembleDebug
cargo test --manifest-path rust-core/Cargo.toml
```
