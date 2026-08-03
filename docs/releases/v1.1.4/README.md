> **English** | [中文](https://github.com/sdf123098/Sparkle-Morpher/blob/main/docs/releases/v1.1.4/README_zh.md)

# Sparkle Morpher 1.1.4

Sparkle Morpher 1.1.4 includes a comprehensive fix for the Touhou Little Maid dedicated server crash, alongside deep refactoring and optimizations for remote player model animation and movement smoothness in multiplayer mode.

---

## 🐛 Touhou Little Maid Dedicated Server Crash Fix

- **Eliminated `NoClassDefFoundError: LocalPlayer`**: Fixed a critical crash where interacting (`mobInteract`) with Touhou Little Maids on a dedicated server threw a missing client-only class exception (`LocalPlayer`) and crashed the server thread.
- **Strict Server/Client Boundary Separation**: Refactored mod compatibility logic. All methods referencing `Minecraft`, `LocalPlayer`, or client GUIs have been isolated into dedicated client-only classes (`TouhouLittleMaidClientCompat`). Servicable bytecode and constant pools on dedicated servers now contain **zero client-side class references**.
- **All Variants & Loaders Covered**: The fix is applied across all 6 build variants: Fabric 1.21.1 / 26.1.x / 26.2 and NeoForge 1.21.1 / 26.1.x / 26.2.

---

## ⚡ Remote Player Animation & Movement Smoothness Optimization

- **Fixed 20Hz Stuttering & Micro-Freezes**: Anchored asynchronous animation evaluation timebases at submission time (captured on the render thread). This eliminates time phase leaps and monotonicity freezes caused by worker thread execution delays crossing client tick boundaries.
- **On-Demand Render Evaluation**: Replaced indiscriminate frame-start batching with on-demand worker task submission (only submitting when a visible entity is being rendered in the current frame). Non-visible or frustum-culled entities no longer waste worker thread pool resources.
- **Remote Player Walk Cycle Interpolation**: Remote players now default to using the same per-frame `walkAnimation` position and speed interpolation as local players. This eliminates phase lead (overly fast leg swinging) and abrupt velocity threshold cutoffs, resulting in natural and fluid remote player movement animations in multiplayer.

---

## 📢 Community & Documentation

- Added Telegram official group link to all language READMEs: [https://t.me/sparklemorpher](https://t.me/sparklemorpher)
- Join our Telegram group and Discord community to report bugs and suggest new features!

---

## 📦 Release Artifacts (JARs)

- `sparkle-morpher-1.1.4-fa1.21.1.jar` (Fabric 1.21.1 / Java 21)
- `sparkle-morpher-1.1.4-fa26.1.x.jar` (Fabric 26.1.x / Java 25)
- `sparkle-morpher-1.1.4-fa26.2.jar` (Fabric 26.2 / Java 25)
- `sparkle-morpher-1.1.4-neo1.21.1.jar` (NeoForge 1.21.1 / Java 21)
- `sparkle-morpher-1.1.4-neo26.1.x.jar` (NeoForge 26.1.x / Java 25)
- `sparkle-morpher-1.1.4-neo26.2.jar` (NeoForge 26.2 / Java 25)
