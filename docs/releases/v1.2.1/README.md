> **English** | [简体中文](https://github.com/sdf123098/Sparkle-Morpher/blob/main/docs/releases/v1.2.1/README_zh.md)

# Sparkle Morpher 1.2.1

Sparkle Morpher 1.2.1 fixes a singleplayer crash on the Fabric 26.x line, aligns the 26.2 branch with the real 26.2 APIs, and brings the rendering/network/Maid refactor series R0–R11 (recorded between 2026-08-10 and 2026-08-13, including everything from the 08-12 prerelease `1.2.0-beta-R11MAID`) into a formal release across Fabric and NeoForge × 1.21.1 / 26.1.2 / 26.2.

---

## Highlights

### Crash fixes (focus of this release)

- **Fixed `IncompatibleClassChangeError` on joining singleplayer** (Fabric 26.1.2 / 26.2): removed the compile-time shadow stubs where `Minecraft.execute/submit/isLocalServer` were wrongly declared `static`, which made `Minecraft.getInstance().execute(...)` compile to `invokestatic` and crash at runtime against the real instance methods. Everything is now compiled back to `invokevirtual` (verified by a full ASM scan: 0 remaining).
- **Fa26.2 stub cleanup & alignment with real 26.2 APIs**: removed 24 vestigial/shadowing stubs, keeping only 4 runtime compat layers (`MultiBufferSource` / `IrisApi` / `ModConfig` / `IForgeGuiGraphicsExtractor`); 6 call sites (`getScoreboard` / `renderNames` / `renderBuffers` / `getMainRenderTarget` / `screen` etc.) moved to the real 26.2 APIs.
- **Sync packet queue overflow → bounded backpressure**: bursts no longer abort the whole sync when the queue briefly fills; the enqueue side now waits with a bounded budget (server bandwidth limiting + outbound drain form a backpressure loop).

### Rendering & performance

- **Classic HUD deep performance rewrite**: FBO partial physical-pixel offscreen cache (was ~35 ms full CPU render per frame → ~0.17 ms); GPU path with a 60 Hz independent refresh budget, SIMD/compat path at 10 Hz throttle; 8-slot bone SSBO ring + `GL_STREAM_DRAW` streaming uploads, eliminating shared-buffer sync stalls; explicit alpha compositing and batching isolation.
- **First-person hand rendering fix** (26.x): the hand always uses the `entityTranslucent` pass.
- **Preview rotation pollution fix** (1.21.1): entity yaw and preview mode are reset even on exception paths (try/finally).
- **Hand rendering NPE guard**: `getAnimationBundle()==null` mid-state protection (12 sites across 6 branches).
- **R10-series internal refactors**: `RenderBackend` interface isolation (Blaze3D / OpenGL / SIMD / Java implementations), `GpuMeshRegistry` leases + orphan sweeps, unified resource ownership for model assemblies (deterministic release), weighted-LRU audio cache.

### Networking & model sync

- **Fixed crash when clients without SPM join**: per-player channel pre-checks before sending (NeoForge/Fabric) + batch send fallback.
- **Systemic fix for sync timeouts / stuck "receiving model data"**: `LegacySyncFlowControl` 64 KiB burst backpressure + 512 MiB in-flight budget; a dedicated single-threaded sync executor with a bounded ordered packet queue (decrypt/verify/disk-write no longer blocks network/main threads); termination frame sent when the server cache is unavailable so the client never hangs forever; the watchdog no longer reports false success.
- **neo1.21.1 stuck-at-LOADING fix**: `toClientboundPacket` null packet → wrapped in `ClientboundCustomPayloadPacket`.
- **R9 networking modularization** (internal): send-entry pre-checks, connection-state split, upload-transport interfaces.

### Maid (TLM) compatibility (R11)

- `MaidModelSync` divergence converged across branches (byte-identical in all six).
- **`C2SSetMaidModelPacket` (id 24) ported to all branches**: maid model switching now uses SPM's own chain (auth pipeline); the official TLM protocol remains as fallback.
- TLM-environment startup crash fixed (`@Mixin(targets=...)` to avoid `MixinTargetAlreadyLoaded`).
- 26.x GUI maid preview not replacing YSM models — fixed.
- R11 `compat.api` service layer (internal, no startup-order dependencies).

### GUI / HUD

- **Classic / Modern HUD dual toggles**: independent switches in settings; the legacy keybinding and old screen were removed.
- **`ClassicHudLayoutScreen` layout editor**: drag, unbounded scaling, scroll zoom, yaw rotation, one-click reset.
- **`ModernHudRenderer` independent entry contract** (off by default, internal).

### Other fixes

- Abnormal head rotation while riding (`RiderRotationMath` pure-angle helper, verified on 6 branches).
- 1.21.1 default model rotation not following the camera (cross-frame cache registry retained).
- Server model pack name JSON-quote display fix.

### Internal refactors (R0–R9, core of 1.2.1)

- S0 security hotfixes: YSM folder path-escape sandbox, atomic audio-cache dedup.
- R2 unified thread pools (`SmExecutors` bounded queues + CallerRuns backpressure + TaskScope).
- R3 `ModelStoragePaths` centralization + `PersistentStore` atomic writes.
- R4 unified resource container (folder / zip GBK / limits) + zip-bomb protection.
- R5 Model Domain / R6 EntityModelResolver (priority + revision race protection).
- R7 `ClientModelManager` split (3024 lines → 5 focused classes).
- R8 `ServerModelManager` split (1899 lines → 8 focused classes, atomic file moves + upload policy).
- **Tests grown from 45 → 201 per branch** (YsmCrypt golden vectors, model corpus, race acceptance).

---

## Files (vanilla full builds)

| File | Platform |
|---|---|
| `sparkle-morpher-1.2.1-fa1.21.1.jar` | Fabric 1.21.1 |
| `sparkle-morpher-1.2.1-fa26.1.x.jar` | Fabric 26.1.2 |
| `sparkle-morpher-1.2.1-fa26.2.jar` | Fabric 26.2 |
| `sparkle-morpher-1.2.1-neo1.21.1.jar` | NeoForge 1.21.1 |
| `sparkle-morpher-1.2.1-neo26.1.x.jar` | NeoForge 26.1.2 |
| `sparkle-morpher-1.2.1-neo26.2.jar` | NeoForge 26.2 |

> CurseForge builds (no natives) live in the Curseforge directory and are not attached to this release.
