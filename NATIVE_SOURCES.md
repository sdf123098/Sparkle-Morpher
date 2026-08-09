# Native code sources and reproducible builds

Sparkle Morpher packages the `ysm-core` native libraries for accelerated model
rendering. This document records the exact public source, build definition, and
hashes for the native files included with this release line.

## Source provenance

- Original upstream: [OpenYSMDev/openysm.cpp](https://github.com/OpenYSMDev/openysm.cpp)
- Upstream base commit: [`3e86bb01b370ecf225d8d89442e6089840f999c5`](https://github.com/OpenYSMDev/openysm.cpp/commit/3e86bb01b370ecf225d8d89442e6089840f999c5)
- Modified source used by Sparkle Morpher: [sdf123098/openysm.cpp](https://github.com/sdf123098/openysm.cpp)
- Exact source commit: [`382e46c98e9fdd64c6852ecb493d4db1bbb43835`](https://github.com/sdf123098/openysm.cpp/commit/382e46c98e9fdd64c6852ecb493d4db1bbb43835)
- License: MIT; see the fork's [LICENSE](https://github.com/sdf123098/openysm.cpp/blob/382e46c98e9fdd64c6852ecb493d4db1bbb43835/LICENSE)
- Modifications: [MODIFICATIONS.md](https://github.com/sdf123098/openysm.cpp/blob/382e46c98e9fdd64c6852ecb493d4db1bbb43835/MODIFICATIONS.md)
- Third-party header notices: [THIRD_PARTY_NOTICES.md](https://github.com/sdf123098/openysm.cpp/blob/382e46c98e9fdd64c6852ecb493d4db1bbb43835/THIRD_PARTY_NOTICES.md)

The fork contains the modified `dllmain.cpp`, `build.zig`, Zig manifest, JNI
headers, and SSE2NEON header used by the build.

## Transparent build

The fork's [GitHub Actions workflow](https://github.com/sdf123098/openysm.cpp/blob/382e46c98e9fdd64c6852ecb493d4db1bbb43835/.github/workflows/build-natives.yml)
builds all packaged targets on Windows with Zig 0.16.0 and Android NDK
27.2.12479018, then uploads the generated libraries and SHA-256 sums as an
Actions artifact.

Equivalent local build command:

```powershell
zig build -Dplatform=all -Drelease -Dandroid-ndk="$env:ANDROID_NDK_ROOT"
```

Outputs are written to `zig-out/<platform>/`.

## Packaged binary verification

Each binary below was compared byte-for-byte by SHA-256 with the corresponding
output from the source tree at the commit above.

| Target | Packaged file | SHA-256 |
| --- | --- | --- |
| Windows x64 | `natives/windows-x64/ysm-core.dll` | `68552E0BD336988D6AA7C012B2B5355E677B77E2E95BB9F8902F30ED5E773D04` |
| Windows x86 | `natives/windows-x86/ysm-core.dll` | `1A2AF959C097815F2235F209D380D5E353FDD9B3A0B2799E09F67EEC0843211B` |
| Linux x64 | `natives/linux-x64/libysm-core.so` | `432BFD4EE33A03A5ACB1BE4CAFD2507C8CC79160EF7DEDA000F4F3B7C03C7087` |
| macOS x64 | `natives/macos-x64/libysm-core.dylib` | `B0AB5494403833AC080851B6805B0C38EE38051F9CB2B1AB9B93F59BD0176F39` |
| macOS ARM64 | `natives/macos-arm64/libysm-core.dylib` | `E2A591CB7464C1B492305C24FB98994FE2B1E2023B781CFAC0033F1770B774E2` |
| Android ARM64 | `natives/android-arm64/libysm-core.so` | `5890A9CFF20500238B49170E2633C68D3CD5A9A91C890E7F9A5B84E36E87EA5B` |

The Java project also checks the expected JNI ABI through
`gradle/verify-native-abi.gradle`.
