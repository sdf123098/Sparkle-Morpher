# Sparkle Morpher 1.2.2

## Cross-Backend Performance & Stabilization / Core Modernization Series

Release date: 2026-08-23
Supported branches: Fabric 1.21.1, Fabric 26.1.2, Fabric 26.2, NeoForge 1.21.1, NeoForge 26.1.2, NeoForge 26.2

> **English** | [中文](RELEASE_NOTES_1.2.2_zh-CN.md)

Sparkle Morpher 1.2.2 delivers the main work planned for the 1.2.x “Cross-Backend Performance & Stabilization” phase. All six actual source repositories were synchronized, built, verified, and pushed to their matching GitHub branches.

### Highlights

- Modern HUD modernization and stabilization:
  - 26.x uses explicit VAO, SSBO, BoneSkinShader, and a 144-byte-per-bone layout for the unified skeleton path.
  - Restored the separate translucent blend stage and alpha handling for transparent materials.
  - Fixed layout anchors, AABB/FBO framing, glow-bone exclusion, pose snapshots, scale, and yaw handling.
  - Fixed classic HUD `input_vertical` direction contamination and lost context during deferred world-model submit replay.
  - Every Modern HUD rendering, layout, and title option is marked “Experimental”. Modern HUD remains disabled by default while Classic HUD remains enabled by default.

- Minecraft 26.1.2/26.2 held-item crash fix:
  - 26.x Modern HUD now uses Mojang’s GUI item RenderState extraction API for main-hand and off-hand items.
  - Removed the erroneous manual `ItemEntityRenderState` creation path, which submitted an item through the GUI deferred/Picture-in-Picture pipeline to an `EntityRenderDispatcher` with no assigned renderer.
  - Main hand, off hand, Scale, Yaw, and HUD Layout behavior are preserved; 1.21.1 keeps its version-specific legacy API.

- Cross-backend rendering and performance:
  - Roulette stage 1 now uses cached geometry through `GuiGraphicsExtractor.fill`, avoiding raw GL/CommandEncoder submissions during extraction that were later overwritten; a CPU fallback keeps visibility consistent across OpenGL and Vulkan.
  - The 26.2 Vulkan path detects the actual Minecraft `GpuDevice`, uses the 144-byte-per-bone Vulkan ABI and Native SIMD bone calculation, and falls back to Java when the native path is not applicable.
  - The implementation keeps business logic behind the backend-neutral Blaze3D/vanilla boundary instead of coupling it to raw OpenGL/Vulkan.

- Compatibility and reliability:
  - Fixed TouhouLittleMaid sitting-state access and custom-model state synchronization across all six loader/version branches.
  - Fixed the 1.21.1 item-buffer/glint immediate path (Issue #18) and the Fabric 26.x flight animation frame-boundary stutter.
  - Added or tightened architecture-boundary, RenderState contract, material contract, and six-branch build checks while retaining the required 1.21.1 and 26.x adapters.

### Build and verification

- All six branches were pushed with `git push`; each local HEAD was then compared with its matching remote branch after fetch.
- A clean release build completed on 2026-08-23 and produced a 1.2.2 original JAR for every branch. The six original JARs in this release are the primary downloads.
- Full build output is included as `BUILD_LOG_1.2.2_2026-08-23.txt`; branch logs were collected under `.buildlogs/6branches/`.
- This release does not claim real Minecraft 26.2 Vulkan client visual regression coverage; that still requires an actual Vulkan client environment. Fabric 26.2 also retains pre-existing ArchitectureBoundaryTest violations, which are not presented as fixed by this release.

### Release assets

- Six original Fabric/NeoForge JARs.
- Chinese notes, English notes, and the build log.

The 1.2.2 scope freeze remains in effect: the large Action Runtime, Paper Doll, Import Pipeline, Cloud, and PortableGpuRenderPath rewrites are out of scope for this release.
