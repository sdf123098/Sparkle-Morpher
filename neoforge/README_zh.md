# Sparkle's Morpher (SPM) — 花火火的变身器

> [English](README.md) | **中文** | [日本語](README_ja.md) | [한국어](README_ko.md)

一个**纯客户端 Minecraft 自定义模型模组**。用自定义 3D 模型、贴图、动画与音效装扮角色，在任何 Minecraft 服务器上，与同样安装 SPM 的玩家互相看见彼此的模型。

**只需在客户端安装 SPM，Minecraft 服务器无需安装 SPM、插件或进行改造。** 多人模型共享通过 SPM Cloud 实现：双方连接同一个 Cloud 实例，绑定当前游戏身份，并选择对方有权访问的 Cloud 模型。

**QQ:** 1104823534 | **Discord:** [加入 Discord](https://discord.gg/3KqK7USF39) | **Telegram:** [加入 Telegram](https://t.me/sparklemorpher) | **Patreon:** [cw/Soid211](https://www.patreon.com/cw/Soid211) | **爱发电:** [Micaftic](https://afdian.com/a/Micaftic)

[快速开始](#quick-start) · [多人互相可见](#multiplayer) · [模型格式](#model-formats) · [功能](#features) · [支持的构建](#supported-builds) · [SPM Cloud](#spm-cloud) · [兼容性](#compatibility) · [常见问题](#faq) · [致谢](#credits)

<a id="quick-start"></a>
## 快速开始

1. 在 [Releases](https://github.com/sdf123098/Sparkle-Morpher/releases) 下载与你的 **Minecraft 版本和加载器**匹配的构建。六种构建见下表。
2. 将 SPM `.jar` 放入客户端的 `mods` 文件夹。Fabric 构建还需要 Fabric API；如果所选发行包要求其他依赖，请一并安装。使用对应的 Fabric 或 NeoForge 配置启动 Minecraft。
3. 进入世界或服务器，按 **Alt + Y** 打开模型面板。导入本地模型或选择 Cloud 模型，再选择贴图与设置。按 **Z** 打开动画转盘。按键可在 Minecraft 的控制设置中修改。
4. 如需互相可见，双方选择**同一个 Cloud 实例**，登录并绑定当前游戏身份。选择公开的 Cloud 模型，或上传自己的模型并设为公开。确认隐私模式已关闭。对方的 SPM 客户端会自动下载并渲染有权访问的模型。

使用本地模型无需配置 Cloud。仅在本地导入文件不会自动上传或共享模型。

<a id="multiplayer"></a>
## 在任何服务器上互相看见模型

SPM 的模型共享独立于 Minecraft 服务器的模组配置。无论是原版服、插件服还是模组服，都无需请服主安装 SPM。

| 使用情况 | 显示效果 |
|---|---|
| 双方安装 SPM、连接同一 Cloud、完成游戏身份验证，并使用有权访问的 Cloud 模型 | 各客户端显示对方的自定义模型与贴图；受支持的模型设置及转盘/待机动作也会同步。 |
| 对方没有安装 SPM | 对方看到你的普通 Minecraft 外观。 |
| 模型仅在本地导入 | 可以在本地使用，不会自动通过 Cloud 共享。 |
| 双方使用不同 Cloud、身份未绑定，或无权访问模型 | 解决这些条件之前，双方无法通过 Cloud 共享外观。 |

模型资源与外观更新在 **SPM 客户端和 SPM Cloud** 之间传输。Minecraft 服务器继续处理游戏逻辑。模型替换改变客户端渲染，不改变服务器规则、碰撞箱或权限。

<a id="model-formats"></a>
## 模型格式

| 格式 | 导入支持 |
|---|---|
| `.ysm` | 带有贴图和模型动画的 YSM 模型，使用基于 OpenYSM/YSMParser 的导入管线。 |
| `.bbmodel` | Blockbench 项目中的立方体/网格几何、骨骼层级、贴图及受支持的动画。 |
| `.zip` | 按内容识别 YSM 文件夹、Blockbench 模型包、Figura 头像包与 Bedrock 模型包。 |
| Bedrock 几何 | `.geo.json` / `geometry.json`；Bedrock 模型包还可包含 `.animation.json` 文件与 PNG 贴图。 |
| `.gltf` / `.glb` | 本地 glTF 模型导入。请保留 `.gltf` 引用的配套外部资源。 |

Figura 包导入读取模型与贴图，不提供 Figura Lua 运行环境。支持导入某种格式不代表支持原软件的所有功能。Cloud 上传与共享以所选实例支持的格式为准。

<a id="features"></a>
## 功能

- **自定义外观：**替换玩家模型、切换贴图，调整模型提供的自定义设置。
- **动画转盘：**按 Z 选择模型自带动作；受支持的模型可使用动画控制器、`loop` / `once` / `hold` 播放模式及 Molang 表达式。
- **模型音效：**播放模型自带语音与音效，支持 Opus 音频解码。
- **模型管理：**从本地文件、目录或 URL 导入，浏览、分组和收藏模型。
- **Cloud 模型库：**浏览全部模型、最近使用、收藏、我的模型和公开模型；全部模型直接列出可访问模型，无需搜索词。
- **更多模型目标：**受支持的实体、载具与投射物也可使用自定义模型；具体目标取决于模型及所选构建的联动支持。

<a id="supported-builds"></a>
## 支持的构建

请选择适用于客户端的构建。Fabric 与 NeoForge 分别提供独立发行包。

| 构建 | 加载器 | Minecraft | Git 分支 |
|---|---|---|---|
| Sparkle-Morpher-Fa1.21.1 | Fabric | 1.21.1 | `main` |
| Sparkle-Morpher-Fa26.1.2 | Fabric | 26.1.2 | `fa26.1.2` |
| Sparkle-Morpher-Fa26.2 | Fabric | 26.2 | `fa26.2` |
| Sparkle-Morpher-Neo1.21.1 | NeoForge | 1.21.1 | `neo1.21.1` |
| Sparkle-Morpher-Neo26.1.2 | NeoForge | 26.1.2 | `neo26.1.2` |
| Sparkle-Morpher-Neo26.2 | NeoForge | 26.2 | `neo26.2` |

Minecraft 1.21.1 使用 Java 21，Minecraft 26.1.2 / 26.2 使用 Java 25。具体依赖以对应发行包的要求为准。一起游玩时，请使用 Cloud 协议兼容的构建。

<a id="spm-cloud"></a>
## SPM Cloud：官方或自建

可以使用内置官方 Cloud，也可以连接社区或自建实例。**SPM Cloud 是独立的模型与同步服务，不是 Minecraft 服务端模组。** 希望互相共享外观的玩家需要选择同一个实例。

- 使用游戏账号登录，或使用 Cloud 账号登录后绑定当前游戏身份。可用认证服务与注册方式由 Cloud 运营者决定。
- 已绑定账号可自动恢复登录，失败后会间隔重试；手动退出后暂停自动恢复，重新选择实例后恢复。
- 每个实例的账号、模型和身份绑定相互独立。切换实例不会迁移模型库或身份绑定。
- 私有模型不会自动变为公开。希望在多人游戏中普遍可见时，请使用公开模型；仅选用私有模型不会赋予其他玩家访问权限。

自建部署请参阅独立的 [SPM Cloud Rust 后端](https://github.com/sdf123098/spm-cloud)及其 [English](https://github.com/sdf123098/spm-cloud/blob/main/README.md) / [中文](https://github.com/sdf123098/spm-cloud/blob/main/README_zh.md)搭建说明。支持 Docker Compose、Linux 原生和 Windows 原生部署。自定义认证网关与多个身份提供方由 Cloud 运营者配置；这些配置属于 Cloud 服务，无需改动 Minecraft 服务器。

<a id="compatibility"></a>
## 模组兼容性

SPM 包含 Better Combat、Curios、Create、Iris/Sodium 和皮肤层渲染的联动支持。实际可用性取决于 Minecraft 版本、加载器及其他模组的版本，该列表不保证任意组合均可兼容。使用 SPM 本身无需为了可选联动而安装这些模组。

<a id="faq"></a>
## 常见问题

### Minecraft 服务器需要安装 SPM 吗？

不需要。在希望显示自定义模型的客户端安装 SPM 即可，Cloud 独立于 Minecraft 服务器处理外观共享。

### 双方都安装了 SPM，为什么仍看不到彼此的模型？

检查双方是否连接同一个 Cloud 实例、已登录、已绑定并验证当前游戏身份，以及是否关闭隐私模式。请选择有权访问的 Cloud 模型，而非仅在本地导入的模型，并检查 Cloud 连接状态。自建实例如果缺少动作同步，还需确认后端支持当前动画协议。

### 不使用 Cloud 也能使用 SPM 吗？

可以在本地使用模型。通过 Cloud 进行多人共享需要正常的 Cloud 连接。Cloud 不可用时，共享与新模型下载可能中断。

### 可以使用外置认证服务吗？

请选择所用 Cloud 实例已启用的认证服务，并验证当前游戏身份。Minecraft 服务器的登录模式本身不会自动生成已验证的 Cloud 身份绑定。

### 不同语言的 README 内容一致吗？

英文、中文、日文和韩文采用相同的章节顺序、安装步骤、多人共享条件与构建表。章节 ID 统一，因此 `#multiplayer` 等链接在各语言中均可使用。阅读任何语言版本，使用要求都相同。

<a id="credits"></a>
## 致谢与许可

- 基于 [OpenYSM](https://github.com/OpenYSM)（MIT）开发。
- 使用 [OpenYSMDev/YSMParser](https://github.com/OpenYSMDev/YSMParser)（MIT）进行 YSM 模型解析。
- 默认模型库：[sdf123098/YSM-Model](https://github.com/sdf123098/YSM-Model)。
- Blockbench：[JannisX11/Blockbench](https://github.com/JannisX11/blockbench)。

SPM 使用 [MIT](LICENSE) 许可证。模型资源遵循各自作者的许可与使用条款。
