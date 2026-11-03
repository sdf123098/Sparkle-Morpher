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
| Windows x64 | `natives/windows-x64/ysm-core.dll` | `BE9220EB779E332C3D3AEC1C1C56A23EBECE9455755B042F2A686FB17930550A` |
| Windows x86 | `natives/windows-x86/ysm-core.dll` | `38FB99931E022A29172A9D34930C6D115844CA420BD7DE5A88D2FEC12E606C37` |
| Linux x64 | `natives/linux-x64/libysm-core.so` | `5A70BD270CFFA461973606452EDE720C688E83A48DF8AE2B63003EC3F6788811` |
| macOS x64 | `natives/macos-x64/libysm-core.dylib` | `10A1F4DE36EC3CA69DED52A357F64E50FBCACF7C3CC4DBCDD69224FE07D55954` |
| macOS ARM64 | `natives/macos-arm64/libysm-core.dylib` | `DB2CAC51E7F875DFC49BBFAD5E52BF7462D73BBAB51B874D3FDE235AD194DCB9` |
| Android ARM64 | `natives/android-arm64/libysm-core.so` | `12D55085B2ADB7B524458C6F161C5D4FC0F0DA9CBE2933ED226E73695C3864A4` |

The Java project also checks the expected JNI ABI through
`gradle/verify-native-abi.gradle`.
