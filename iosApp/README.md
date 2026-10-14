# iOS app

The SwiftUI app calls the same Rust UniFFI API as Android. `shared/iosMain` remains a KMP
gateway placeholder; the iOS UI currently uses the generated Swift binding directly.

Requirements: Xcode, XcodeGen, Rust, and both `aarch64-apple-ios-sim` and
`aarch64-apple-ios` Rust targets. From the repository root:

```bash
rustup target add aarch64-apple-ios-sim aarch64-apple-ios
./scripts/build-rust-ios.sh
xcodebuild -project iosApp/CoolapkKMP.xcodeproj -scheme CoolapkKMP \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' build
```

The script generates UniFFI Swift bindings, builds both Rust static libraries, packages
an XCFramework, and generates the Xcode project. Build artifacts stay under ignored
`build/` and `rust-core/target/`; generated Swift sources are in `iosApp/Generated`.
