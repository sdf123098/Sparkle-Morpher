> **English** | [中文版](https://github.com/sdf123098/Sparkle-Morpher/blob/main/docs/releases/v1.1.5/README_zh.md)

# Sparkle Morpher 1.1.5

Sparkle Morpher 1.1.5 is a hotfix release that resolves a critical `NoClassDefFoundError` in Fabric 1.21.1 caused by a package path mismatch for the Touhou Little Maid client compatibility implementation.

---

## 🐛 Touhou Little Maid Compatibility Hotfix (Fabric 1.21.1)

- **Fixed `NoClassDefFoundError` on Fabric 1.21.1**: Under the Fabric 1.21.1 multi-module setup (Architectury), the common `client.compat` expected `com.micaftic.morpher.client.compat.touhoulittlemaid.fabric.TouhouLittleMaidClientCompatImpl`. The implementation was incorrectly located under `core.compat.touhoulittlemaid.fabric`, causing `@ExpectPlatform` lookup to fail at runtime.
- **Package Relocation & Visibility Adjustments**:
  - Relocated `TouhouLittleMaidClientCompatImpl.java` to `client.compat.touhoulittlemaid.fabric` and updated imports.
  - Made `TouhouLittleMaidAccess.java` and its static methods `public` so it can be safely accessed across packages.
- **Comprehensive Audit**:
  - **Fabric 1.21.1**: Audited 40+ `@ExpectPlatform` bindings; `TouhouLittleMaidClientCompatImpl` was the only misplaced class and is now fixed.
  - **Fabric 26.x & NeoForge (All Versions)**: Use single-module compile units and direct FQN calls without `@ExpectPlatform`, unaffected by this issue.

---

## 📦 Included Release Artifacts

- `sparkle-morpher-1.1.5-fa1.21.1.jar` (Fabric 1.21.1 / Java 21)
- `sparkle-morpher-1.1.5-fa26.1.x.jar` (Fabric 26.1.x / Java 25)
- `sparkle-morpher-1.1.5-fa26.2.jar` (Fabric 26.2 / Java 25)
- `sparkle-morpher-1.1.5-neo1.21.1.jar` (NeoForge 1.21.1 / Java 21)
- `sparkle-morpher-1.1.5-neo26.1.x.jar` (NeoForge 26.1.x / Java 25)
- `sparkle-morpher-1.1.5-neo26.2.jar` (NeoForge 26.2 / Java 25)
