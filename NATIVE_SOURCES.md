# Native code sources and reproducible builds

Sparkle's Morpher packages the `spm_core_native` Rust SIMD libraries for
accelerated model bone evaluation and rigid vertex skinning. This document records
the exact source repository, build definition, and SHA-256 hashes for the native
binaries bundled with this release line.

## Source provenance

- Upstream independent core repository: [AIH-Interactive/spm-core](https://github.com/AIH-Interactive/spm-core)
- Source revision: `166402d4707e01b17a9b87a8631e18768eb3c1eb`
- Rust workspace: `native-rust/` (`spm-native-abi`, `spm-render-scalar`, `spm-render-simd`, `spm-native-jni`)
- License: MIT; see the repository `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md`.

## Reproducible build

The `spm-core` GitHub Actions workflow (`core.yml`) and local Rust toolchain build
all packaged targets with locked Cargo dependencies (`Cargo.lock`), and verify
the JNI/C ABI boundary before loading.

```powershell
cd native-rust
cargo build --workspace --release --locked
```

## Packaged binary verification

Each binary in `common/src/main/resources/natives/` matches the corresponding
SHA-256 digest in `common/src/main/resources/native-manifest.json`:

| Target | Packaged file | SHA-256 |
| --- | --- | --- |
| Windows x64 | `natives/windows-x64/spm_core_native.dll` | `0F399A91AECC0A201D6CE26D6E9BB6727BA26D33C9D2DAA906D2BCEE7923C087` |
| Windows x86 | `natives/windows-x86/spm_core_native.dll` | `6E91B6560E465AFE540B090167259E63B6A8075F80E9439CEA3E3153B2420690` |
| Linux x64 | `natives/linux-x64/libspm_core_native.so` | `24C85D286F59DEDA33E91AD3BF9383743D745236F734B2BC5C67FD128424156D` |
| Linux ARM64 | `natives/linux-arm64/libspm_core_native.so` | `FEF3797E4964AFF198E228FB15320BB79A949BD58DE9D73519E2AB759BA052CC` |
| macOS x64 | `natives/macos-x64/libspm_core_native.dylib` | `619DA06023BF393FB1ACADCED860F67A18566A2892E79ED9079B68188A2E4E75` |
| macOS ARM64 | `natives/macos-arm64/libspm_core_native.dylib` | `EECE73913C8B02B9E8793EFF445B98AE39F0D2BFCF8176103565B862D4CEF3B0` |
| Android ARM64 | `natives/android-arm64/libspm_core_native.so` | `AA858F464A18115A6C4170714515D7D431D65B9AC42F8E5D5F128A8315314A62` |

The Java project verifies these digests and expected JNI ABI symbols through
`gradle/verify-native-abi.gradle`.
