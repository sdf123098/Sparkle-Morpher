# Native code sources and reproducible builds

Sparkle Morpher packages the `ysm-core` native libraries for accelerated model
rendering. This document records the exact public source, build definition, and
hashes for the native files included with this release line.

## Source provenance

- Original upstream: [OpenYSMDev/openysm.cpp](https://github.com/OpenYSMDev/openysm.cpp)
- Upstream base commit: [`3e86bb01b370ecf225d8d89442e6089840f999c5`](https://github.com/OpenYSMDev/openysm.cpp/commit/3e86bb01b370ecf225d8d89442e6089840f999c5)
- Modified source used by Sparkle Morpher: [sdf123098/openysm.cpp](https://github.com/sdf123098/openysm.cpp)
- Exact source commit: [`8d572acf572e62817de5a08086df2188d91c989f`](https://github.com/sdf123098/openysm.cpp/commit/8d572acf572e62817de5a08086df2188d91c989f)
- License: MIT; see the fork's [LICENSE](https://github.com/sdf123098/openysm.cpp/blob/8d572acf572e62817de5a08086df2188d91c989f/LICENSE)
- Modifications: [MODIFICATIONS.md](https://github.com/sdf123098/openysm.cpp/blob/8d572acf572e62817de5a08086df2188d91c989f/MODIFICATIONS.md)
- Third-party header notices: [THIRD_PARTY_NOTICES.md](https://github.com/sdf123098/openysm.cpp/blob/8d572acf572e62817de5a08086df2188d91c989f/THIRD_PARTY_NOTICES.md)

The fork contains the modified `dllmain.cpp`, `build.zig`, Zig manifest, JNI
headers, and SSE2NEON header used by the build.

## Transparent build

The fork's [GitHub Actions workflow](https://github.com/sdf123098/openysm.cpp/blob/8d572acf572e62817de5a08086df2188d91c989f/.github/workflows/build-natives.yml)
builds all packaged targets on Windows with Zig 0.16.0 and Android NDK
25.2.9519653, then uploads the generated libraries and SHA-256 sums as an
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
| Windows x64 | `natives/windows-x64/ysm-core.dll` | `5929C863177FD46433F486234ECE2CF225B5F75AFB2D4A04877D60D349186C8D` |
| Windows x86 | `natives/windows-x86/ysm-core.dll` | `B38B5743C55F73F9255094C001A890A8B2BC02C9AAA5BCF74BAD38FCAD7E5091` |
| Linux x64 | `natives/linux-x64/libysm-core.so` | `68E3EE6689CF7A6326E8D073F680AA074DEDAFBDB31EAEC968C7C30B4C55EF04` |
| macOS x64 | `natives/macos-x64/libysm-core.dylib` | `AC683CC59D5E71A94EAC9C4709F5EED3C472879D4418FA67D121580577FFF328` |
| macOS ARM64 | `natives/macos-arm64/libysm-core.dylib` | `33C6AA6DD36D925E81E4EF4D83F4E7F40D803D4E511BECE2B8F84E039F5822EA` |
| Android ARM64 | `natives/android-arm64/libysm-core.so` | `062A3D3C99FBFD15A59486F2684C9CB7BFF20DF199929FEB6C8E00A6DC3BAAD8` |

The Java project also checks the expected JNI ABI through
`gradle/verify-native-abi.gradle`.
