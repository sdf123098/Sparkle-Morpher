# Sparkle Morpher 1.2.5

## Player Visual Runtime / Render Pipeline and Developer Tooling

Release date: 2026-09-11
Supported branches: Fabric 1.21.1, Fabric 26.1.2, Fabric 26.2, NeoForge 1.21.1, NeoForge 26.1.2, NeoForge 26.2

> **English** | [中文](RELEASE_NOTES_1.2.5_zh-CN.md)

Sparkle Morpher 1.2.5 delivers the **Player Visual Runtime** stage of the 1.2.x plan, adds a default-off Blaze3D frame-graph render channel, and gives model authors a unified **Developer options** panel. All six actual source repositories were synchronized, cleanly built, and pushed to their matching GitHub branches.

### Highlights

#### Player Visual Runtime (render context convergence)

Render state that used to be scattered across ThreadLocals and ad-hoc boolean flags is now centralised in `RenderContext`:

- `RenderPass` gains `FIRST_PERSON` and `PAPER_DOLL`, appended at the end so existing ordinal semantics stay unchanged.
- `RenderContext` moves from `ThreadLocal<RenderPass>` to an immutable record `ThreadLocal<RenderScope>(pass, modelPreview, entity, partialTick)`. The old API (`enter`/`restore`/`currentPass`/`isGuiPreview`/`isOldHud`) keeps its previous semantics.
- First-person detection converges on `RenderContext`: the standalone `FIRST_PERSON_MODE` field is removed while the global PBR and FirstPerson-mod fallbacks remain. A worker-thread behaviour audit confirmed this is safe — worker threads already read the default value and receive their semantics through method parameters.
- The model-preview flag joins `RenderScope`, with a new `isAnyPreview()` collapsing the two union reads of "model preview ∪ GUI preview".
- A new `PhysicsDomain` (WORLD / PREVIEW / EXTRA_PLAYER / FIRST_PERSON) makes the physics domain explicit.
- New `PlayerFrameSnapshot` (frame-level render inputs plus a testable `sanitizePartialTick`) and `PlayerRenderPolicy` (pure decision logic with no Minecraft dependency, unit-testable) pull render gating out of call sites and into policy inputs.

#### Default-off Blaze3D frame-graph render channel

A new channel registers GPU draws as deferred draws and inserts them into the Minecraft frame graph as a first-class pass:

```text
Submit stage   IGeoRenderer → register (deep-copied pose)
Frame graph    inject at the tail of LevelRenderer frame-graph construction,
               declaring readsAndWrites(main)
MC scheduling  Minecraft owns ordering, barriers, and resource lifetime
```

- Key finding: player shadows are submitted independently (relative position and shape only, no model geometry), so GPU skinning and shadows do not conflict — the channel does not have to sacrifice shadows.
- **Conservative gating (first version covers only the most ordinary case)**: requires the config switch and the experimental switch, plus world rendering, no preview, not first person, no shader pack, non-glowing entity, opaque, and an empty part mask. Everything else keeps using the existing paths.
- **Fail-safe**: the whole chain is wrapped in `try/catch`; an exception is logged once and the frame's pending draws are cleared, with unconditional cleanup at frame begin/end.
- The switch is a standard mod config entry (`EnableBlaze3DInPipelineDraw`, default off, in the Performance group) and **requires no JVM arguments**. Two one-shot INFO lines that bypass the log gating (`[SM-BLAZE3D]`) were added so users who enable only the new channel still see output.
- Adjacent improvements in the same batch: select the translucent pipeline from `translucentTexture` (#2); replace the "backend name equals VULKAN" check with capability-based routing (#3, OpenGL fast-path behaviour unchanged); precompile both pipelines before the first draw while retaining lazy compilation as a fallback (#4).

> This channel is **off by default** and represents a conservative first version intended to be opened up gradually. Translucent textures, glowing entities, split glow bones, preview, and first-person scenes still use the existing paths.

#### Model author "Developer options"

The former Debug group is promoted to Developer options and split into four sections — **Panel state / Logging / Profiling / Renderer diagnostics** (renderer diagnostics exists only on the 26.x branches, since four of its entries are version-specific):

- **Panel state**: dump the current panel state to the clipboard as JSON, reset panel state, and a **default-off** "apply panel state from clipboard" feature for reproducing the UI state an author reports. Writeback validates the whole batch first and applies it all-or-nothing; any invalid value rejects the batch, and the confirmation panel (which needs host context) cannot be restored this way.
- **Logging / Profiling**: two switches that previously existed in config but were never surfaced in the UI are now wired up (network online debug log, warn repeated animation evaluation).
- Panel state is now cached per context key: reopening the same target remembers its tab, search, and scroll position, while different targets no longer share state.

#### Compatibility fixes

- **ParCool (three NeoForge branches)**: the NeoForge side previously had no loader implementation, so ParCool actions never entered animation resolution. A pure-reflection bridge (no compile-time dependency) maps the 38 action names from the bundled animation JSON and remembers direction per action instance to avoid mid-playback drift; `ctrl.parcool_loaded` and `ctrl.parcool_doing` Molang variables are provided as well. The Fabric side keeps its no-op stub (ParCool is NeoForge-only).
- **SlashBlade (NeoForge 1.21.1, Issue #25)**: fixed attack animations being flipped vertically and horizontally, and the sheath being bound to the wrong side at the wrong height. The root cause was applying vanilla layer-space maths directly in YSM entity space without the `scale(-1,-1,1)` and vertical baseline compensation; the project's elytra and parrot layers already compensated explicitly, and only this branch had been missed. The maid's held-blade render branch was added too.
- **Render backend decision drift**: `RenderBackendDecision` on neo26.2 / neo26.1.2 had missed one synchronized commit, which could route GUI previews onto the direct-draw path. It is now aligned on `isAnyPreview()` and all four 26.x branches are byte-identical at that site.

#### Stability and other fixes

- **Model face-culling escape hatch**: a default-off self-rescue entry for "some models lose faces at certain angles", covering the GPU, Blaze3D, and SIMD paths.
- **Native trust chain now fails closed**: the `YSM_CORE_LIB` development override previously skipped content verification, while the native ABI version is identical before and after the culling fix, so a stale library could not be identified from it. The override now verifies the content hash against the manifest by default, refuses to load on mismatch, and prints the expected and actual hashes; an explicit escape hatch remains. Cache reuse and reinstall both log the manifest identity, making "which native build is actually loaded" visible in the log.
- **Blaze3D mesh resource leak fixed**: the mesh cache never released its GPU buffers or per-frame direct memory, and the release path only fired when the OpenGL handle was non-zero; release hooks were added and reclamation is now unconditional.

#### Dead code removal

Removed render paths verified unreferenced across all source sets: `PiePortableRenderPath` / `PieMesh` / `PiePipeline` / `PieShader`, `IrisRenderPath`, `BoneXformCompute`, and the shader assets that became unused with them. The live `Pie` fallback draw is retained.

### Build and verification

- All six branches were pushed and `fetch`-verified so that local HEAD matches the corresponding remote branch; working trees are clean.
- The release build script performed clean builds (JDK 21 for 1.21.1, JDK 25 for 26.x) and all six branches produced full JARs; CurseForge variants are additionally checked to be free of native libraries.
- Tests: the full suite passes on Fabric 26.2 and NeoForge 26.2 (345 / 317 tests, 0 failures), including the new render-context, frame-snapshot, render-policy, backend-routing, and frame-pass tests added in this version.
- The remaining branches were compiled with their relevant tests.

### Release assets

- Six full JARs (Fabric / NeoForge × 1.21.1 / 26.1.2 / 26.2).
- Bilingual release notes (this file and the Chinese version).

### Compatibility matrix

| Loader | Minecraft | Branch | Artifact |
|---|---:|---|---|
| Fabric | 26.2 | `fa26.2` | `sparkle-morpher-1.2.5-fa26.2.jar` |
| Fabric | 26.1.2 | `fa26.1.2` | `sparkle-morpher-1.2.5-fa26.1.x.jar` |
| Fabric | 1.21.1 | `main` | `sparkle-morpher-1.2.5-fa1.21.1.jar` |
| NeoForge | 26.2 | `neo26.2` | `sparkle-morpher-1.2.5-neo26.2.jar` |
| NeoForge | 26.1.2 | `neo26.1.2` | `sparkle-morpher-1.2.5-neo26.1.x.jar` |
| NeoForge | 1.21.1 | `neo1.21.1` | `sparkle-morpher-1.2.5-neo1.21.1.jar` |

Use the artifact matching both your loader and your Minecraft version. The 1.21.1 builds require the 1.21.1 loader/API line; do not mix them with 26.x installations.

### Known limitations

- The new Blaze3D frame-graph channel is off by default with conservative gating and is not yet opened up for every scenario (translucent, glowing entities, preview, first person, shader packs).
- Panel-state writeback is off by default and is a model-author troubleshooting tool.
- The 1.21.1 branches still carry a shared static projection field whose value depends on call order; that semantic fragility has not been addressed yet.
