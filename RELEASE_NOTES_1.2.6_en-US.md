# Sparkle Morpher 1.2.6

## Responsive 3D card catalog / i18n completion / Paper Doll split and ride fixes

Release date: 2026-09-12
Supported branches: Fabric 1.21.1, Fabric 26.1.2, Fabric 26.2, NeoForge 1.21.1, NeoForge 26.1.2, NeoForge 26.2

> **English** | [中文](RELEASE_NOTES_1.2.6_zh-CN.md)

Sparkle Morpher 1.2.6 brings a **responsive live-3D card catalog** to the model picker, adds **Traditional Chinese (Hong Kong) and Literary Chinese (lzh)** while localizing built-in model metadata into **15 languages**, splits the player paper-doll rendering out of a single file into a maintainable preview package with a set of classic HUD doll options, and fixes two bugs: **Happy Ghast sit pose** and **non-player model selection leaking into the player's own selection**. All six source repositories are synchronized, clean-built, and pushed to their GitHub branches.

### Highlights

#### Responsive 3D card catalog (model picker)

The model picker gains a three-state view, cycled from a bottom-toolbar button through **Auto → Cards → List** (config `ModelPickerStyle`, default `Auto`, session-pinned after an in-UI switch):

- **Auto**: uses cards when space allows, otherwise falls back to the original icon + text grid. The list path is fully retained, so a complete rollback is always possible.
- **Cards**: forces card mode, preferring cards even when only one minimum readable card (40×48) fits; falls back only below that.
- **List**: the existing icon + text grid.

**Every card face is a live 3D model.** The first design rendered live 3D only for hovered/selected cards (at most two preview entities) and used static covers elsewhere; on real hardware that produced mostly blank cards and "no model unless hovered". It now renders every visible card's model live — the model is the card face. Cost is bounded by a per-page-slot preview entity pool (slots are rebound on page turns and do not grow with catalog size); lazy models show a placeholder until ready.

Card-art geometry is now calibrated against the real YSM spec:

- The card-art size is confirmed as **52×90** (aspect 1.7308); `CARD_ASPECT` was corrected from 1.62 to `90/52`, removing the side letterboxing.
- The **`gui_background` → live 3D → `gui_foreground` border** three-layer order was restored; the earlier two-layer simplification lost the front border and darkened the background.
- Doll scale changed from `coverH*0.62` to the long-validated `coverH*0.43`, with a hard "never exceed cover height" clamp on `figureScale()`, fixing head clipping.
- Column count is now chosen by **searching for the column count that maximizes cards per page** (ties prefer larger cards): horizontal fill 99–100%, vertical 72–98% (limited by the 52:90 ratio).

Catalog defects fixed in the same batch (each with its root cause):

- `ModelPanelLayout.create()` decided the vertical-tab layout from `guiScale >= 3.0`, but `guiScale` is a physical/logical ratio unrelated to available logical space, so layouts flipped on monitor or scale changes. It now tests **logical width/height**.
- The fixed 16px `nameBandH` floor could exceed the card height on very short cards, producing a negative `coverH`; now `min(band, cellH - 1)` with a regression test.
- `markModelUsed` was called after the readiness check, so not-yet-ready cards were never marked in-use and lazy/TTL eviction could reclaim the model being viewed; it now **marks before checking readiness**.

> Card look (cover shading, doll size, name-band wrap threshold, palette) was built headless and has not been calibrated item-by-item in-game; models without embedded card art show live 3D only and get no static placeholder. Card mode intentionally has no scrollbar (paging by design).

#### Traditional Chinese (Hong Kong) and Literary Chinese (lzh)

**Root cause**: Minecraft's language fallback chain is only `[en_us, selected locale]` — there is **no sibling or same-family fallback**. Only `zh_tw.json` existed, so selecting "香港繁體 / zh_hk" resolved all mod text to English. There is no `zh_sg`/`zh_my`/other Simplified locale in vanilla, so no Simplified file was added.

- **`zh_hk.json` (new)**: generated from `zh_tw.json` as the base, **not via OpenCC** (its `t2hk` mixes Simplified into Traditional). Only two verified HK glyph adaptations (`平臺→平台`, `臥→卧`); key order and structure match `zh_tw`, with just 13 differing values.
- **`lzh.json` (Literary Chinese, new)**: built by aligning vanilla 1.21.1 `zh_cn.json` and `lzh.json` into a **4852-entry Simplified→Literary glossary**, then translating **789 unique English strings** across 8 parallel passes under a style guide and the Touhou Little Maid `lzh.json` reference; key order matches `en_us.json` exactly.

#### Built-in model metadata localization

Built-in model names/descriptions/authors previously fell back to English only (`ModelMetadataPresenter.getLocalizedString` recognizes `en_us`). It now covers **15 languages**, up from `{en_us, zh_cn}`: added `zh_tw`, `zh_hk`, plus `es_es`, `fr_fr`, `id_id`, `ja_jp`, `ko_kr`, `pt_br`, `ru_ru`, `tr_tr`, `uk_ua`, `vi_vn`, `lzh`. For the ten pre-existing non-Chinese locales, **611 unique English strings** that were missing or still equal to English were translated.

- One real defect fixed: `ru_ru.json`'s `open_model_folder.tips` used Cyrillic `п`/`а` in place of Latin `n` (`§папка`), breaking vanilla format codes; corrected.
- All 11 groups of "identical English, different Chinese meaning" keys were reviewed, and the 3 genuinely ambiguous ones were rewritten.

> Translations were spot-checked, **not proofread by native speakers** (~1400 entries across 10 languages plus Literary Chinese). 12 placeholder warnings were confirmed pre-existing and harmless via `git diff` (dead keys or correct argument counts) and left untouched.

#### Paper Doll split and classic HUD doll options

On the 26.x branches, the 999-line `ModelPreviewRenderer.java` was split into a **compatibility facade (999 → 177 lines, all 25/25 public APIs preserved, zero call-site migration)** plus a new `client/renderer/preview/` package: `PreviewSceneRenderer` (bed/ground/vehicle preview props), `PreviewMath` (deliberately MC-free projection and mouse math for pure-JVM tests), `PreviewRenderBridge` (GUI preview queue and `InventoryScreen` handshake), `GuiModelRenderer`, `PaperDollRenderer`, `PaperDollLayout` (pure math, including the inverse), `PaperDollVisibilityPolicy`, and `PaperDollActivity`. Tests added: `PaperDollIsolationTest`, `PaperDollHeadModeContractTest`, `PaperDollLayoutTest`, `PaperDollVisibilityPolicyTest`.

The classic HUD doll gains a set of options (config section `extra_player_render`, **defaults equal historical behavior**, so installing the jar changes nothing until the config is edited):

| Option | Config key | Default | Meaning |
|---|---|---|---|
| Head mode | `ClassicHudHeadMode` | `STRAIGHT` | `STRAIGHT` head follows body (legacy); `FOLLOW` head tracks the real view yaw, clamped ±85° |
| Anchor | `ClassicHudAnchor` | `TOP_LEFT` | 9-position anchor plus X/Y offset; `TOP_LEFT` with the original pixel value is the exact legacy position |
| Idle auto-hide | `ClassicHudAutoHideIdleSeconds` | `0` (off) | Hide the doll after N idle seconds |
| Hide in third person | `ClassicHudHideInThirdPerson` | `false` | Hide in third-person view |
| Vehicle alignment | `ClassicHudAlignWithVehicle` | `false` | Align doll yaw with the vehicle |
| Action-only visibility | `ClassicHudShowOnActionOnly` | `false` | Show only while moving/sneaking/swimming/gliding/climbing/in water/riding/using/attacking/hurt/sleeping |

> These options are **config-file only — there is no GUI settings screen**.

#### Ride and selection fixes

- **Happy Ghast sit pose used the wrong animation**: `LivingMovementAnimationPredicate` used `Saddleable` (saddleable mobs) for riding on 1.21.1, but 26.x replaced it with a `vehicle instanceof LivingEntity → ride` fallback after the API was removed. `HappyGhast extends Animal → LivingEntity`, so it was treated as a straddle vehicle and played the horse-style ride pose. It is now explicitly matched **before** the fallback and routed to `sit`, matching the mod's own `sit` ("sitting on an entity") semantics. Only the four 26.x branches are affected (1.21.1 has no Happy Ghast).
- **Setting a model for a non-player polluted the player's own selection**: `ModernPlayerModelScreen#applyModelAndTexture`'s custom-target branch (maid/NPC) unconditionally called `ClientModelManager.rememberSelectedModel(...)`, which writes the player selection track and persists it; on the next join `restorePersistedModelSelection()` then applied the target entity's model to the player. A new `rememberPlayerSelection` flag now defaults to `true` only for the player's own panel, with `shouldRememberPlayerSelection()` / `setRememberPlayerSelection(boolean)` exposed so third-party NPC panels can override it precisely.

#### Blaze3D frame-graph channel (experimental, default off)

Diagnosis of "most in-world models vanish when enabled": geometries were successfully deferred (the log shows `[SM-BLAZE3D] ... ACTIVE`) and removed from the vanilla collector, but the frame-graph pass **never executed** them, so models were neither in the collector nor drawn — hence invisible. Six alternatives were ruled out: config not applied, mixin not loaded, missing target signature, unreachable injection point, accessor mismatch, and frame-graph culling.

Rather than add a high-privilege mechanism, this release ships a **self-healing fallback plus staged diagnostics**: new per-frame counters, and if a frame deferred draws that were not all rendered (pass never ran, ran late, or was refused), the channel is **permanently auto-disabled for the session** with a lost-geometry log line, falling back to the normal submit path — so the defect can affect at most one frame. Registration exceptions now self-heal too.

> The **true root cause is not confirmed**; the diagnosis document's retest section is still awaiting user logs. This release delivers a self-healing fallback and diagnostics, **not a proven repair**. The channel is **default off and experimental**, requiring explicit opt-in.

### Build and verification

- All six branches were pushed and `fetch`-verified so that local HEAD matches the corresponding remote branch; working trees are clean.
- The release build script performed clean builds (JDK 21 for 1.21.1, JDK 25 for 26.x) and all six branches produced full JARs; CurseForge variants are additionally checked to be free of native libraries.
- The full unit-test suite passes on all six branches — **2204 tests, 0 failures** (1 skipped per branch):
  Fabric 1.21.1 **365**, Fabric 26.1.2 **386**, Fabric 26.2 **393**, NeoForge 1.21.1 **338**, NeoForge 26.1.2 **357**, NeoForge 26.2 **365**.
- Note: the 1.21.1 branches require **JDK 21** (JDK 25 makes Gradle fail when creating the test task with `Type T not present`); 26.x uses JDK 25. 1.21.1 uses a two-project `common` + `fabric` layout, where the tests live under `:common:test` (`:fabric:test` is NO-SOURCE).

### Release assets

- Six full JARs (Fabric / NeoForge × 1.21.1 / 26.1.2 / 26.2).
- Bilingual release notes (this file and the Chinese version).

### Compatibility matrix

| Loader | Minecraft | Branch | Artifact |
|---|---:|---|---|
| Fabric | 26.2 | `fa26.2` | `sparkle-morpher-1.2.6-fa26.2.jar` |
| Fabric | 26.1.2 | `fa26.1.2` | `sparkle-morpher-1.2.6-fa26.1.x.jar` |
| Fabric | 1.21.1 | `main` | `sparkle-morpher-1.2.6-fa1.21.1.jar` |
| NeoForge | 26.2 | `neo26.2` | `sparkle-morpher-1.2.6-neo26.2.jar` |
| NeoForge | 26.1.2 | `neo26.1.2` | `sparkle-morpher-1.2.6-neo26.1.x.jar` |
| NeoForge | 1.21.1 | `neo1.21.1` | `sparkle-morpher-1.2.6-neo1.21.1.jar` |

Use the artifact matching both your loader and your Minecraft version. The 1.21.1 builds require the 1.21.1 loader/API line; do not mix them with 26.x installations.

### Known limitations

- The **Blaze3D frame-graph channel** is default-off and experimental; its true root cause is unconfirmed, and this release is a self-healing fallback with diagnostics.
- The **classic HUD doll options** are config-file only, with no GUI; defaults equal historical behavior.
- **Paper-doll snapshot reuse (Slice E) is not implemented**: 26.2's `ModernHudRenderer.renderAt` intentionally does not consume the pose snapshot and a contract test requires that line to remain; consuming the world snapshot makes complex models' bone matrices be overwritten by secondary evaluation (hair/accessory/limb ghosting). It needs in-game reproduction and a read-only sharing design.
- **Head pitch and "follow both" head modes are not implemented** (yaw follow only), and **Rotation Unlock is not implemented** (needs camera/input-layer changes, cross-module).
- The 1.21.1 branches do not carry the preview package split (same semantics only, on the FBO path).
- Card-mode look and low-end FPS (up to 8×2=16 live previews per page) still need in-game feedback.
- The 1.21.1 branches still carry a shared static projection field whose value depends on call order; that semantic fragility has not been addressed yet.
- The 1.2.6 card-catalog work was done headless and has not had item-by-item in-game visual acceptance.
