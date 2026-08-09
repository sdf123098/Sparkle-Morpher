> **English** | [简体中文](https://github.com/sdf123098/Sparkle-Morpher/blob/main/docs/releases/v1.2.0/README_zh.md)

# SparkleMorpher 1.2.0

SparkleMorpher 1.2.0 expands Bedrock and Blockbench model compatibility, improves model loading and cache recovery, and delivers a set of rendering, animation, and gameplay hotfixes across Fabric and NeoForge (based on fixes recorded between 2026-08-06 and 2026-08-09).

---

## Highlights

### Model and format compatibility

- **Direct Bedrock resource loading**: import bare `.geo.json` / `*geometry.json` files and Bedrock resource packs (zip detection), with geometry selection by identifier (case-insensitive), multiple geometries, and optional attachments such as capes. Common Bedrock animation variables, the `-this` expression, and action-name mapping are supported.
- **Blockbench per-cube rotation**: support `element.rotation` from `minecraft:geometry` 1.12.0+ exports (cubes rotating around their own pivot), unified across both mesh and cube conversion paths.
- **Bedrock-style limb naming**: bone semantics for `armLeft`/`armRight`/`legLeft`/`legRight`, and case-insensitive armor-part matching.
- **bbmodel animation mirror fix**: convert Blockbench XYZ Euler angles to equivalent rotations in the renderer's order, eliminating mirrored or incorrectly played multi-axis animations; add action fallbacks for six high-frequency states (`death`, `sleep`, `swim`, `climb`, `climbing`, `attacked`).
- **Client render compatibility API**: expose model and texture lifecycle events to optional renderers (Caustica, Iris, etc.) through a common interface; official compatibility submodules ship as nested JARs, so a single main mod jar is enough.

### Reliability and performance

- **Model cache self-healing**: server cache uses atomic writes, validation before sending, and instant rebuild from the source model; the client validates decrypted/decompressed content and rejects damaged data, reducing manual dual-end cache clearing.
- **Cache identity no longer includes the mod jar hash**: rebuilding or replacing the jar at the same version no longer forces a full re-download (only version changes invalidate the cache).
- **Model-folder robustness**: unrelated files (notes, images, videos, etc.) are safely ignored, fixing stutters/OOM from large files being fully read into memory; startup cache validation moved off the critical loading path (async).
- **Roulette performance**: corrected backend detection plus fallback scanline geometry caching on 26.x OpenGL; Vulkan mode now uses triangle-strip meshes, eliminating single-digit FPS from per-pixel CPU rendering.
- Fix concurrent model parsing races that produced `ProcessorPipeline` null-processor exceptions and repeated log spam (deduplicated and rate-limited per model).
- Sanitize NaN/Inf produced by YSM molang (fixes flying/flickering hair and tails); clean up 26.2 test log noise.
- Fix GUI preview crashes caused by parallel-controller blending between keyframes and transition points, and crashes from GUI preview entities without an assigned entity ID.
- Fix the 1.21.1 NPE crash when opening the model selection screen (animation bundle accessed before the model is ready), and fix built-in default models failing to load under some launchers (union resource scheme support).

### Interaction and animation fixes

- `/ysm model disable` can now be used by regular players on themselves to restore the vanilla appearance; `reload` and `set` remain OP level 2 operations.
- Fix roulette selection so the selected animation key is the one that plays (1.21.1 misaligned roulette actions + legacy model extra-animation parsing), and fix the center-icon artifact after leaving the 26.2 roulette settings screen with ESC.
- Fix the inability to switch YSM maid models in survival mode (Issue #11, compatible with the TLM 26.x maid ownership API change).
- Improve Touhou Little Maid and large-modpack compatibility: fix the root cause of `MixinTargetAlreadyLoaded` startup crashes (the Mixin configuration phase no longer defines entity classes early via `Class.forName`), and fix the 26.2 maid issues where ESC could not exit the YSM model selection screen and maids did not render the selected YSM model (adapted to the 26.2 screen API migration).
- Fix vanilla 26.x spear/trident animation namespace conflicts; fix the looping lance animation (lance actions now use `hold_on_last_frame`).
- Empty-hand left click: removed mod-specific empty-hand logic (models can trigger their own attack animations via `ctrl.swing('mainhand', 'empty')`), keeping item-attack left-click and cross-tick spear input deduplication.
- Fix residual previous-state animations caused by empty-string placeholders in state animation arrays (no more walking/swinging while standing still).
- In 26.2, custom projectile models registered as `minecraft:arrow` now also apply to arrow subclasses when no more specific projectile model is supplied.

---

## Included Release Artifacts

- `sparkle-morpher-1.2.0-fa1.21.1.jar` — Fabric 1.21.1 / Java 21
- `sparkle-morpher-1.2.0-fa26.1.x.jar` — Fabric 26.1.x / Java 25
- `sparkle-morpher-1.2.0-fa26.2.jar` — Fabric 26.2 / Java 25
- `sparkle-morpher-1.2.0-neo1.21.1.jar` — NeoForge 1.21.1 / Java 21
- `sparkle-morpher-1.2.0-neo26.1.x.jar` — NeoForge 26.1.x / Java 25
- `sparkle-morpher-1.2.0-neo26.2.jar` — NeoForge 26.2 / Java 25

---

## Verification

All six maintained loader/version variants were verified with clean builds. CurseForge-targeted packages were also checked to ensure that they contain no native executable libraries.
