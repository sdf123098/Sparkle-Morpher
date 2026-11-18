# Sparkle's Morpher (SPM)

> **English** | [中文](README_zh.md) | [日本語](README_ja.md) | [한국어](README_ko.md)

A **client-only Minecraft custom model mod**. Give your character a new look with custom 3D models, textures, animations and sound effects, and see each other's models on any Minecraft server with other SPM users.

**Install SPM on your client. The Minecraft server needs no SPM mod, plugin or changes.** Multiplayer model sharing uses SPM Cloud: both players connect to the same Cloud instance, bind their current game identities and select Cloud models accessible to the other player.

**QQ:** 1104823534 | **Discord:** [Join Discord](https://discord.gg/3KqK7USF39) | **Telegram:** [Join Telegram](https://t.me/sparklemorpher) | **Patreon:** [cw/Soid211](https://www.patreon.com/cw/Soid211) | **Afdian:** [Micaftic](https://afdian.com/a/Micaftic)

[Quick start](#quick-start) · [Multiplayer](#multiplayer) · [Model formats](#model-formats) · [Features](#features) · [Supported builds](#supported-builds) · [SPM Cloud](#spm-cloud) · [Compatibility](#compatibility) · [FAQ](#faq) · [Upstream & license](#credits)

<a id="quick-start"></a>
## Quick start

1. Download the build matching your **Minecraft version and loader** from [Releases](https://github.com/sdf123098/Sparkle-Morpher/releases). See the supported builds below.
2. Put the SPM `.jar` in your client's `mods` folder. Fabric builds also require Fabric API; install any additional dependencies requested by your chosen release. Launch Minecraft with the matching Fabric or NeoForge profile.
3. Join a world or server and press **Alt + Y** to open the model panel. Import a local model or choose one from Cloud, then select its texture and settings. Press **Z** to open the animation wheel. Key bindings can be changed in Minecraft's Controls menu.
4. To share your appearance, both players select the **same Cloud instance**, sign in and bind their current game identities. Select a public Cloud model, or upload your own model and make it public. Check that privacy mode is off. The other SPM client downloads and renders the accessible model automatically.

Local models can be used without setting up Cloud. Importing a file locally does not automatically upload or share it.

<a id="multiplayer"></a>
## See each other's models on any server

SPM's model sharing works independently of the Minecraft server's mod setup. You can use it on vanilla, plugin-based and modded servers without asking the server operator to install SPM.

| Situation | What players see |
|---|---|
| Both players have SPM, use the same Cloud, have verified game identities and use accessible Cloud models | Each client displays the other player's custom model and texture; supported model settings and wheel/idle actions also synchronize. |
| The other player does not have SPM | They see your normal Minecraft appearance. |
| A model is imported only locally | You can use it locally; it is not automatically shared through Cloud. |
| The players use different Cloud instances, have unbound identities or cannot access the model | Cloud appearance sharing is unavailable between them until those conditions are resolved. |

Model assets and appearance updates travel between **SPM clients and SPM Cloud**. The Minecraft server continues to handle gameplay. Model replacement changes client rendering; it does not change server rules, hitboxes or permissions.

<a id="model-formats"></a>
## Model formats

| Format | Import support |
|---|---|
| `.ysm` | YSM models with textures and model-provided animations, parsed and evaluated by the independent [spm-core](https://github.com/AIH-Interactive/spm-core) engine (versions `2.3.2` and earlier used the OpenYSM/YSMParser pipeline). |
| `.bbmodel` | Blockbench projects with cube/mesh geometry, bone hierarchies, textures and supported animations. |
| `.zip` | Content detection for YSM folders, Blockbench model packs, Figura avatar packs and Bedrock model packs. |
| Bedrock geometry | `.geo.json` / `geometry.json`; Bedrock packs can also contain `.animation.json` files and PNG textures. |
| `.gltf` / `.glb` | Local glTF model import. Keep external resources referenced by `.gltf` alongside the model. |

Figura pack import reads models, textures, and audio, and runs supported `.lua` scripts via [spm-core](https://github.com/AIH-Interactive/spm-core)'s bounded pure-Java Lua 5.3 runtime. Importing a format does not imply 1:1 parity with every feature of its original application. Cloud upload and sharing depend on the formats supported by the selected instance.

<a id="features"></a>
## Features

- **Custom appearance:** replace the player model, switch textures and adjust settings exposed by the model.
- **Animation wheel:** select model-provided actions with Z, with animation controllers, `loop` / `once` / `hold` playback and Molang expressions for supported models.
- **Model audio:** play model-provided voice lines and sound effects, including Opus audio decoding.
- **Model library:** import from local files, folders or URLs; browse, group and favorite models.
- **Cloud library:** browse All Models, Recent, Favorites, My Models and Public Models. All Models lists accessible models without requiring a search term.
- **Additional model targets:** supported entities, vehicles and projectiles can also use custom models; available targets depend on the model and integrations in the chosen build.

<a id="supported-builds"></a>
## Supported builds

Choose the build for your client. Fabric and NeoForge releases are separate downloads.

| Variant | Loader | Minecraft | Git branch |
|---|---|---|---|
| Sparkle-Morpher-1.21.1 | Fabric + NeoForge | 1.21.1 | `1.21.1` |
| Sparkle-Morpher-26.1.2 | Fabric + NeoForge | 26.1.2 | `26.1.2` |
| Sparkle-Morpher-26.2 | Fabric + NeoForge | 26.2 | `main` |
| Sparkle-Morpher-26.3 | Fabric + NeoForge | 26.3 | `26.3` |

Use Java 21 for Minecraft 1.21.1 and Java 25 for Minecraft 26.1.2 / 26.2 / 26.3. Follow the dependency requirements of the specific release. Choose builds with compatible Cloud protocols when playing together.

<a id="spm-cloud"></a>
## SPM Cloud: official or self-hosted

Use the built-in official Cloud, or connect to a community/self-hosted instance. **SPM Cloud is a separate model and synchronization service, not a Minecraft server mod.** Players who want to share appearances must choose the same instance.

- Sign in with your game account, or use a Cloud account and bind your current game identity. Available authentication providers and registration options are controlled by the Cloud operator.
- Bound accounts can restore login automatically and retry after failures. Manual logout pauses automatic restoration until an instance is selected again.
- Accounts, models and identity bindings are separate for each instance. Switching instances does not move your library or bindings.
- Private models are not automatically made public. Use a public model for general multiplayer visibility; merely selecting a private model does not grant other players access.

For self-hosting, see the independent [SPM Cloud Rust backend](https://github.com/sdf123098/spm-cloud) and its [English](https://github.com/sdf123098/spm-cloud/blob/main/README.md) / [中文](https://github.com/sdf123098/spm-cloud/blob/main/README_zh.md) setup guides. Deployment supports Docker Compose, native Linux and native Windows. Custom authentication gateways and multiple identity providers are configured by the Cloud operator; setup belongs to the Cloud service, not the Minecraft server.

<a id="compatibility"></a>
## Mod compatibility

SPM includes integrations for Better Combat, Curios, Create, Iris/Sodium and skin-layer rendering. Availability depends on the Minecraft version, loader and versions of the other mods; this list is not a guarantee for every combination. Optional integrations do not require you to install those mods just to use SPM.

<a id="faq"></a>
## FAQ

### Does the Minecraft server need SPM?

No. Install SPM on the clients that want to display custom models. Cloud handles appearance sharing independently of the Minecraft server.

### We both installed SPM. Why can't we see each other's models?

Check that both clients use the same Cloud instance, are signed in, have bound and verified their current game identities, and have privacy mode disabled. Use an accessible Cloud model rather than a local-only import, and check the Cloud connection status. For missing action synchronization on a self-hosted instance, also check that its backend supports the current animation protocol.

### Can I use SPM without Cloud?

Yes, for local model use. Cloud-based multiplayer sharing requires a working Cloud connection. A Cloud outage can interrupt sharing and new model downloads.

### Can I use an external authentication service?

Use a provider enabled by your chosen Cloud instance and verify the current game identity. A Minecraft server's login mode alone does not create a verified Cloud binding.

### Are the README translations equivalent?

English, Chinese, Japanese and Korean follow the same section order, installation steps, multiplayer conditions and build table. Section IDs are shared, so links such as `#multiplayer` work in every language. The requirements are the same whichever translation you read.

<a id="credits"></a>
## Upstream repositories, credits & license

### Core engine & upstream basis

- **Core runtime (`2.4.0+`):** powered by **[AIH-Interactive/spm-core](https://github.com/AIH-Interactive/spm-core)** (`1.0.0`, MIT) — an independent, host-neutral Java 17 + Rust SIMD engine for model parsing (`.ysm`, `.bbmodel`, `.geo.json`, `.gltf`/`.glb`, `.figura`, `v3d`), animation/Molang evaluation, bounded pure-Java Lua 5.3 execution, and CPU/SIMD skinning.
  - `spm-core` itself is clean-room written against format and behavior specifications from [YesSteveModel/YesSteveModel](https://github.com/YesSteveModel/YesSteveModel) (Apache-2.0, specification only), [AndreaFrederica/OhMyYesSteveModel](https://github.com/AndreaFrederica/OhMyYesSteveModel) (Apache-2.0, historical format specs, algorithm provenance, and Apache-2.0 synthetic test vectors), [JannisX11/blockbench](https://github.com/JannisX11/blockbench) (GPL-3.0, specification only), [KhronosGroup/glTF](https://github.com/KhronosGroup/glTF) (CC-BY-4.0, specification only), and [FiguraMC/Figura](https://github.com/FiguraMC/Figura) (LGPL-2.1 historical revision, specification only); see `spm-core`'s `provenance/allowed-sources.json` and `THIRD_PARTY_NOTICES.md`.
- **Historical upstream lineage (`2.3.2` and earlier / `2.4.0` transition):**
  - **[OpenYSM](https://github.com/OpenYSM)** (MIT) — the original Minecraft custom-model mod codebase on which SPM `<= 2.3.2` was based (remaining transitional client/GeckoLib3/Molang code in `2.4.0` is being progressively replaced by `spm-core`).
  - **[OpenYSMDev/YSMParser](https://github.com/OpenYSMDev/YSMParser)** (MIT) & **[OpenYSMDev/openysm.cpp](https://github.com/OpenYSMDev/openysm.cpp)** (MIT; formerly forked at [sdf123098/openysm.cpp](https://github.com/sdf123098/openysm.cpp)) — legacy model parser and C++ native acceleration used in `<= 2.3.2`, superseded in `2.4.0+` by `spm-core`.
- **Companion & resource repositories:**
  - Cloud backend: **[sdf123098/spm-cloud](https://github.com/sdf123098/spm-cloud)**
  - Default model library: **[sdf123098/YSM-Model](https://github.com/sdf123098/YSM-Model)**

### License

SPM uses a dual **[All Rights Reserved (ARR) & MIT](LICENSE)** license (original SPM `2.4.0+` mod code, UI, GPU/Blaze3D bridges, Cloud/Peer client, shaders and brand assets are All Rights Reserved; [`spm-core`](https://github.com/AIH-Interactive/spm-core) (`1.0.0`) and historical `<= 2.3.2` / transitional OpenYSM-derived portions remain MIT). Model assets retain their authors' licenses and usage terms.
