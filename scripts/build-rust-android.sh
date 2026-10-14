#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ABI="${1:-arm64-v8a}"
NDK_VERSION="${ANDROID_NDK_VERSION:-27.1.12297006}"

case "$ABI" in
  arm64-v8a)
    RUST_TARGET="aarch64-linux-android"
    CLANG_PREFIX="aarch64-linux-android24-clang"
    CARGO_CC_ENV="CC_aarch64_linux_android"
    CARGO_CXX_ENV="CXX_aarch64_linux_android"
    CARGO_AR_ENV="AR_aarch64_linux_android"
    CARGO_RANLIB_ENV="RANLIB_aarch64_linux_android"
    ;;
  *)
    echo "Unsupported ABI for this milestone: $ABI" >&2
    exit 2
    ;;
esac

if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
  NDK_HOME="$ANDROID_NDK_HOME"
elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  NDK_HOME="$ANDROID_SDK_ROOT/ndk/$NDK_VERSION"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
else
  NDK_HOME="$HOME/Library/Android/sdk/ndk/$NDK_VERSION"
fi

if [[ ! -d "$NDK_HOME" ]]; then
  echo "Android NDK not found: $NDK_HOME" >&2
  echo "Install NDK $NDK_VERSION or set ANDROID_NDK_HOME." >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin) HOST_TAG="darwin-x86_64" ;;
  Linux) HOST_TAG="linux-x86_64" ;;
  *) echo "Unsupported build host: $(uname -s)" >&2; exit 1 ;;
esac

TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
CLANG="$TOOLCHAIN/bin/$CLANG_PREFIX"
if [[ ! -x "$CLANG" ]]; then
  echo "Android clang not found: $CLANG" >&2
  exit 1
fi

export "${CARGO_CC_ENV}=$CLANG"
export "${CARGO_CXX_ENV}=${CLANG_PREFIX/clang/clang++}"
export "${CARGO_AR_ENV}=$TOOLCHAIN/bin/llvm-ar"
export "${CARGO_RANLIB_ENV}=$TOOLCHAIN/bin/llvm-ranlib"
TARGET_UPPER="$(printf '%s' "$RUST_TARGET" | tr '[:lower:]-' '[:upper:]_')"
export "CARGO_TARGET_${TARGET_UPPER}_LINKER=$CLANG"

cargo build \
  --manifest-path "$ROOT_DIR/rust-core/Cargo.toml" \
  --target "$RUST_TARGET" \
  --release

OUTPUT_DIR="$ROOT_DIR/androidApp/src/main/jniLibs/$ABI"
mkdir -p "$OUTPUT_DIR"
cp "$ROOT_DIR/rust-core/target/$RUST_TARGET/release/libcoolapk_core.so" "$OUTPUT_DIR/"

echo "Built $OUTPUT_DIR/libcoolapk_core.so"
