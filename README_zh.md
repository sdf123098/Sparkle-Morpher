# Sparkle's Morpher — 花火火的变身器

> [English](README.md) | **中文** | [日本語](README_ja.md) | [한국어](README_ko.md)

Minecraft 综合自定义模型加载器，让玩家为角色挂载自定义模型、动画与音效——告别一成不变的方块小人。

> 这是一个**综合模型加载器**：当前支持 `.ysm` 格式（基于 OpenYSM，MIT 许可）和 `.bbmodel` 格式（Blockbench），后续会陆续加入对其他主流模型格式的支持。

---

## 功能特性

### 自定义玩家模型与皮肤

用完全自定义的 3D 模型替换原版玩家模型。所有自定义模型在多人游戏中**对其他玩家可见**

### 模型格式支持

- **`.ysm`** — 基于 OpenYSM/YSMParser 的原生格式，支持完整骨骼模型与权重动画。
- **`.bbmodel`** — 直接导入 Blockbench 项目文件。支持网格三角化（N 边形扇形三角化）、UV 归一化、面旋转、膨胀扩展、内嵌 Base64 纹理提取及 PNG IHDR 头解析。
- **Figura 头像包** — 直接导入 Figura `.zip` 压缩包。内置 `ZipModelSniffer` 自动识别并分流 YSM 文件夹、Figura 头像和纯 BBModel 压缩包。

### 动画系统

- **动画转盘**（默认按键：Z）— 径向菜单快速切换当前模型的动作与动画。
- **动画控制器** — 完整支持基于状态机的动画控制器，具备 `loop`（循环）、`once`（单次）和 `hold`（保持）播放模式。
- **Molang 表达式** — 数据点同时支持原始数值和 Molang 表达式字符串，实现动态动画混合。

### 音效系统

播放模型内置的语音和音效，由技能或动作触发。音频解码采用 **Opus** 格式，跨平台原生加速，低延迟播放。

### 多模型管理

- 从本地文件、目录或 URL 导入模型，支持加速下载。
- 按分组和收藏组织管理模型。
- 自动目录扫描，识别 `.ysm`、`.zip` 和 `.bbmodel` 文件。

### 服务端功能

- 服务端可定义模型清单并下发至客户端。
- 可配置黑名单（`config/sparkle_morpher/blacklist.txt`）限制特定模型。
- 通过 Cardinal Components 实体数据实现客户端-服务端模型状态同步。

### 模组兼容性

兼容主流模组：

| 类别 | 兼容模组 |
|------|---------|
| 战斗 | Better Combat |
| 饰品 | Curios |
| 建造与自动化 | Create |
| 渲染 | Iris、Sodium |
| 玩家皮肤 | 皮肤层兼容 |

### 跨平台支持

多个构建变体覆盖所有主流加载器和版本组合：

---

## 工作原理

### 模型导入管线

导入模型文件时，Sparkle's Morpher 会执行智能处理管线：

1. **压缩包嗅探** — 按内容分类：YSM 文件夹、Figura 头像（含 `avatar.json` + `.bbmodel`）、纯 BBModel 压缩包或未知格式。
2. **解析** — `.ysm` 文件经 YSMParser 处理；`.bbmodel` 文件由内置 `BBModelParser` 解析，处理大纲树、立方体/网格元素、纹理、动画和控制器状态。
3. **转换** — 解析数据转换为引擎内部 `RawGeometry` 格式。N 顶点网格面经扇形三角化处理；UV 坐标按纹理分辨率归一化；压缩包中的外部 PNG 纹理优先于内嵌 Base64 源。
4. **渲染** — 转换后的模型在玩家激活时替换原版渲染器，自动隐藏默认玩家模型。

### BBModel 兼容性

完整支持 Blockbench 格式，包括：

- 大纲树的嵌套骨骼层级与父子关系
- 立方体和网格元素的正确面 UV 映射
- 内嵌纹理（Base64）的 PNG 头尺寸检测
- 动画播放与循环模式映射
- Blockbench 5 "free" 格式兼容（精简大纲节点 + `groups[]` 回退）
- 孤立元素处理（未被引用的元素自动归入默认骨骼）

---

## 架构

Sparkle's Morpher 采用**公共核心 + 平台适配器**分层架构：

- **`common`** — 所有变体共享的核心逻辑：模型解析、网格处理、压缩包嗅探、动画控制器、音频解码和 Molang 求值。
- **`fabric`** / **`neoforge`** — 平台特定适配器，处理初始化、网络通信、组件注册和渲染钩子。
- **原生层** — 跨平台原生库，用于 Opus 音频解码。

---

## 依赖

因构建变体而异——详见 `mods.toml`（NeoForge）或 `fabric.mod.json`（Fabric）。Fabric 变体需用户自行安装 Fabric API；其他依赖通过 Jar-in-Jar 内置。

---

## 致谢与许可

- 基于 [OpenYSM](https://github.com/OpenYSM)（MIT 许可证）二次开发。
- 使用 [OpenYSM/YSMParser](https://github.com/OpenYSM/YSMParser)（MIT）进行 `.ysm` 模型解析。
- 默认模型库：[sdf123098/YSM-Model](https://github.com/sdf123098/YSM-Model)。
- Blockbench 格式由 [JannisX11/Blockbench](https://github.com/JannisX11/blockbench) 开发。

**许可证：** MIT
