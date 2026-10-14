#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/rust-core/Cargo.toml"
OUT_DIR="$ROOT_DIR/shared/src/androidMain/kotlin"

case "$(uname -s)" in
  Darwin) LIB_NAME="libcoolapk_core.dylib" ;;
  Linux) LIB_NAME="libcoolapk_core.so" ;;
  *) echo "Unsupported bindgen host: $(uname -s)" >&2; exit 1 ;;
esac

cargo build --manifest-path "$MANIFEST" --features cli --lib

(
  cd "$ROOT_DIR/rust-core"
  cargo run --features cli --bin uniffi-bindgen -- \
    generate \
    --library "target/debug/$LIB_NAME" \
    --language kotlin \
    --out-dir "$OUT_DIR" \
    --no-format
)

echo "Generated UniFFI Kotlin bindings in $OUT_DIR"
