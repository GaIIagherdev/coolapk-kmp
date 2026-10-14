#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUST_DIR="$ROOT_DIR/rust-core"
IOS_DIR="$ROOT_DIR/iosApp"
GENERATED_DIR="$IOS_DIR/Generated"
BUILD_DIR="$IOS_DIR/build"
FRAMEWORK="$BUILD_DIR/CoolapkCore.xcframework"
export IPHONEOS_DEPLOYMENT_TARGET=17.0

for command in cargo rustup xcodebuild xcodegen; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required tool: $command" >&2
    exit 1
  fi
done

for target in aarch64-apple-ios-sim aarch64-apple-ios; do
  if ! rustup target list --installed | grep -Fxq "$target"; then
    echo "Missing Rust target $target. Run: rustup target add $target" >&2
    exit 1
  fi
done

mkdir -p "$GENERATED_DIR" "$BUILD_DIR"
cargo build --locked --manifest-path "$RUST_DIR/Cargo.toml" --features cli --lib
(
  cd "$RUST_DIR"
  cargo run --locked --features cli --bin uniffi-bindgen -- \
    generate --library target/debug/libcoolapk_core.dylib \
    --language swift --out-dir "$GENERATED_DIR" --no-format
)

for target in aarch64-apple-ios-sim aarch64-apple-ios; do
  cargo build --locked --manifest-path "$RUST_DIR/Cargo.toml" --target "$target" --lib
done

# Xcode recognizes a static-library module map only under this name.
HEADERS_DIR="$BUILD_DIR/Headers"
mkdir -p "$HEADERS_DIR"
cp "$GENERATED_DIR/coolapk_coreFFI.h" "$HEADERS_DIR/"
cp "$GENERATED_DIR/coolapk_coreFFI.modulemap" "$HEADERS_DIR/module.modulemap"

if [[ -d "$FRAMEWORK" ]]; then
  rm -rf "$FRAMEWORK"
fi
xcodebuild -create-xcframework \
  -library "$RUST_DIR/target/aarch64-apple-ios-sim/debug/libcoolapk_core.a" -headers "$HEADERS_DIR" \
  -library "$RUST_DIR/target/aarch64-apple-ios/debug/libcoolapk_core.a" -headers "$HEADERS_DIR" \
  -output "$FRAMEWORK"

xcodegen generate --spec "$IOS_DIR/project.yml" --project "$IOS_DIR"
echo "Open $IOS_DIR/CoolapkKMP.xcodeproj or run xcodebuild with scheme CoolapkKMP."
