# Sparkle's Morpher

> **English** | [中文](README_zh.md) | [日本語](README_ja.md) | [한국어](README_ko.md)

A comprehensive Minecraft custom model loader that lets players mount custom models, animations, and sound effects onto players (and select entities, vehicles, and projectiles) — say goodbye to the default blocky character.

> Sparkle's Morpher is a **universal model loader**. It currently supports the `.ysm` format (based on OpenYSM, MIT licensed) and `.bbmodel` format (Blockbench), with support for additional mainstream model formats planned for future releases.

## Features

### Custom Player Models & Skins

Replace vanilla player models with fully custom 3D models. All custom models are **visible to other players in multiplayer**

### Model Format Support

- **`.ysm`** — The native format powered by OpenYSM/YSMParser, supporting full skeletal models with weighted animations.
- **`.bbmodel`** — Direct import of Blockbench project files. Includes mesh triangulation (N-gon fan triangulation), UV normalization, face rotation, inflate expansion, embedded base64 texture extraction, and PNG IHDR header parsing.
- **Figura Avatar Archives** — Import Figura `.zip` packages directly. The built-in `ZipModelSniffer` automatically detects and routes YSM folders, Figura avatars, and plain BBModel zips.

### Animation System

- **Animation Carousel** (default key: Z) — A radial menu to quickly switch between animations and actions for the current model.
- **Animation Controllers** — Full support for state-machine-based animation controllers with `loop`, `once`, and `hold` playback modes.
- **Molang Expressions** — Data points support both raw numeric values and Molang expression strings for dynamic animation blending.

### Sound Effects

Play model-bundled voice lines and sound effects triggered by skills or actions. Audio decoding uses **Opus** with cross-platform native acceleration for low-latency playback.

### Multi-model Management

- Import models from local files, directories, or URLs with accelerated downloading.
- Organize models with grouping and favorites.
- Automatic directory scanning recognizes `.ysm`, `.zip`, and `.bbmodel` files.

### Server-side Features

- Server operators can define model manifests and push models to clients.
- A configurable blacklist (`config/sparkle_morpher/blacklist.txt`) lets servers restrict specific models.
- Client-server model state synchronization via Cardinal Components entity data.

### Mod Compatibility

Designed to work alongside popular mods:

| Category | Compatible Mods |
|----------|----------------|
| Combat | Better Combat |
| Accessories | Curios |
| Building & Automation | Create |
| Rendering | Iris, Sodium |
| Player Skins | Skin layers compatible |

### Cross-platform

More build variants covering all major loader and version combinations:

## How It Works

### Model Import Pipeline

When you import a model file, Sparkle's Morpher runs it through an intelligent pipeline:

1. **Zip Sniffing** — Archives are classified by content: YSM folder, Figura avatar (contains `avatar.json` + `.bbmodel`), plain BBModel zip, or unknown.
2. **Parsing** — `.ysm` files go through YSMParser; `.bbmodel` files are parsed by the built-in `BBModelParser` which handles outliner trees, cube/mesh elements, textures, animations, and controller states.
3. **Conversion** — Parsed data is converted to the engine's internal `RawGeometry` format. Mesh faces with N vertices are triangulated via fan triangulation; UV coordinates are normalized against texture resolution; external PNG textures in zip archives override embedded base64 sources.
4. **Rendering** — The converted model replaces the vanilla player renderer when active, with automatic hiding of the default player model.

### BBModel Compatibility

Full support for Blockbench's format including:

- Outliner tree with nested bone hierarchy and parent-child relationships
- Cube and mesh elements with proper face UV mapping
- Embedded textures (base64) with PNG header dimension detection
- Animation playback with loop mode mapping
- Blockbench 5 "free" format compatibility (thin outliner nodes with `groups[]` fallback)
- Orphan element handling (unreferenced elements auto-assigned to default bone)

## Architecture

Sparkle's Morpher uses a **common + platform adapter** layered architecture:

- **`common`** — Core logic shared across all variants: model parsing, mesh processing, zip sniffing, animation controllers, audio decoding, and Molang evaluation.
- **`fabric`** / **`neoforge`** — Platform-specific adapters for initialization, networking, component registration, and rendering hooks.
- **Native Layer** — Cross-platform native libraries for Opus audio decoding.

## Dependencies

Varies by build variant — see `mods.toml` (NeoForge) or `fabric.mod.json` (Fabric) for specifics. Fabric variants require Fabric API installed separately; other dependencies are bundled via Jar-in-Jar.

## Credits & License

- Built upon [OpenYSM](https://github.com/OpenYSM) (MIT License).
- Uses [OpenYSM/YSMParser](https://github.com/OpenYSM/YSMParser) (MIT) for `.ysm` model parsing.
- Default model library: [sdf123098/YSM-Model](https://github.com/sdf123098/YSM-Model).
- Blockbench format is a product of [JannisX11/Blockbench](https://github.com/JannisX11/blockbench).

**License:** MIT
