# Native code sources and reproducible builds

Sparkle's Morpher packages the `spm_core_native` Rust SIMD libraries for
accelerated model bone evaluation and rigid vertex skinning. This document records
the exact source repository, build definition, and SHA-256 hashes for the native
binaries bundled with this release line.

## Source provenance

- Upstream independent core repository: [AIH-Interactive/spm-core](https://github.com/AIH-Interactive/spm-core)
- Source revision: `3cffa18799a4caff1a6147fa9c0202add60cfa98`
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
| Windows x64 | `natives/windows-x64/spm_core_native.dll` | `9B5AAD7EC12B343D30FB070843F8F74C4700BEE1F4B61D9D908E2A62FCBD695D` |
| Windows x86 | `natives/windows-x86/spm_core_native.dll` | `869A1526E1D11048453FB061624B364922C5F3E399D291846E5AE2D66E770C56` |
| Windows ARM64 | `natives/windows-arm64/spm_core_native.dll` | `65B8E26126508FC7520E3073CBB7001646024667B1FDBA193861BFFAC35A25DC` |
| Linux x64 | `natives/linux-x64/libspm_core_native.so` | `D6023DC65442A2B1976C70A0E8098D5A24756FFC4335C98D034589B5CF7C3A27` |
| Linux ARM64 | `natives/linux-arm64/libspm_core_native.so` | `9A80B93FCC733CD741E4FB9721BF58B27CF989F5F6BA2F4496CCD1DD355B7404` |
| Linux RISC-V 64 | `natives/linux-riscv64/libspm_core_native.so` | `F75A5C88B5C838066A935EA133B855B481AD1604CB9437367D00965B048B87D9` |
| Linux LoongArch64 | `natives/linux-loongarch64/libspm_core_native.so` | `932568C203C8D165ECD5EE95B7F1385833ACDCC066519E3CA2717AE848315F43` |
| macOS x64 | `natives/macos-x64/libspm_core_native.dylib` | `DDEA4FA9D49919725A478CBD2B02D63C3091765296A48B9027B6DAE223C7A334` |
| macOS ARM64 | `natives/macos-arm64/libspm_core_native.dylib` | `ED4963F0B7E0E21179A639DCA377633268E744DC447E13EB2DF46756BB2D8FDC` |
| Android ARM64 | `natives/android-arm64/libspm_core_native.so` | `D3B0698F9EA785C155198E8CC01D8268D0A8417AA1C42DC471213A42C3735FE2` |
| Android ARM32 | `natives/android-arm32/libspm_core_native.so` | `4E10BF0DA5A127EF51C5E3F111DF632E0DFB260BBC02BC8E14A67BE22102AB85` |

The Java project verifies these digests and expected JNI ABI symbols through
`gradle/verify-native-abi.gradle`.
