# Sparkle Morpher 1.2.6

## 响应式 3D 卡片目录 / i18n 补全 / Paper Doll 拆分与骑乘修复

发布日期：2026-09-12
支持分支：Fabric 1.21.1、Fabric 26.1.2、Fabric 26.2、NeoForge 1.21.1、NeoForge 26.1.2、NeoForge 26.2

> [English](RELEASE_NOTES_1.2.6_en-US.md) | **中文**

Sparkle Morpher 1.2.6 为模型选择界面带来一套**响应式实时 3D 卡片目录**，补齐 **繁體（香港）与文言（lzh）** 两套语言并把内置模型的名称/说明本地化到 15 种语言，将玩家纸娃娃（Paper Doll）渲染从单文件拆分为可维护的预览包并开放一组经典 HUD 玩偶选项，同时修复**乐魂坐姿**与**非玩家模型污染玩家选择**两个问题。六个实际代码仓库均已同步、干净构建并推送到对应 GitHub 分支。

### 主要内容

#### 响应式 3D 卡片目录（模型选择界面）

模型选择界面新增三态视图，通过底部工具栏按钮在 **自动 → 卡片 → 列表** 之间循环（配置项 `ModelPickerStyle`，默认 `自动`，在界面内切换后按会话固定）：

- **自动**：空间足够时使用卡片，否则回退到原有的图标 + 文本网格。列表路径完整保留，可随时回退。
- **卡片**：强制卡片模式，即使空间只够一张最小可读卡片（40×48）也优先卡片；空间不足时自动回退。
- **列表**：原有的图标 + 文本网格。

**每张卡面都是实时 3D 模型**。首版设计只对悬停/选中的卡片渲染实时模型（至多 2 个预览实体），其余用静态封面；在真实硬件上这导致多数卡面空白、模型「不悬停就不显示」。现改为**每张可见卡片都实时渲染自己的模型，模型本身就是卡面**。开销由按页槽位索引的预览实体池封顶（翻页时复用槽位，不随目录规模增长），懒加载模型在就绪前显示占位。

卡片美术几何按实际 YSM 规范校正：

- 卡面标准尺寸确认为 **52×90**（长宽比 1.7308），据此把 `CARD_ASPECT` 从 1.62 修正为 `90/52`，消除两侧黑边。
- 恢复 **`gui_background` → 实时 3D → `gui_foreground` 描边** 的三层顺序；此前简化为两层导致正面描边丢失、背景被压暗。
- 玩偶缩放由 `coverH*0.62` 改为经过验证的 `coverH*0.43`，并对 `figureScale()` 施加「不超过封面高度」的硬上限，修复头部裁切。
- 列数改为**搜索使每页卡片数最多的列数**（同数取更大卡片），水平填充率 99–100%、垂直 72–98%（受 52:90 比例限制）。

同批修复的目录缺陷（均含根因）：

- `ModelPanelLayout.create()` 曾用 `guiScale >= 3.0` 决定纵向标签布局，而 `guiScale` 是与可用逻辑空间无关的物理/逻辑比值，切换显示器或缩放即错乱；改为判断**逻辑宽高**。
- `nameBandH` 的 16px 下限在极短卡片上会超过卡高，导致 `coverH` 为负；改为 `min(band, cellH - 1)` 并加回归测试。
- `markModelUsed` 曾在就绪检查之后调用，未就绪卡片不会被标记在用，懒加载/TTL 回收可能把正在浏览的模型回收；改为**先标记再判就绪**。

> 卡片观感（封面明暗、玩偶大小、名称带折行阈值、配色）在无头环境构建，尚未在实机逐项校准；无内嵌卡图的模型仅显示实时 3D，不提供静态占位。卡片模式按设计不带滚动条（分页浏览）。

#### 繁体（香港）与文言（lzh）语言

**根因**：Minecraft 的语言回退链只有 `[en_us, 所选语言]`，**没有同族/兄弟语言回退**。此前只提供 `zh_tw.json`，因此选择「香港繁體 / zh_hk」时全部模组文本回退到英文。已确认原版不存在 `zh_sg`/`zh_my`/其他简体 locale，故不新增简体文件。

- **`zh_hk.json`（新增）**：以 `zh_tw.json` 为基准生成，**未使用 OpenCC**（其 `t2hk` 会把简体混入繁体）。仅两处经核实的港式用字调整（`平臺→平台`、`臥→卧`），键序与结构同 `zh_tw`，仅 13 个取值不同。
- **`lzh.json`（文言，新增）**：以原版 1.21.1 的 `zh_cn.json` 与 `lzh.json` 对齐，构建 **4852 条「简体 → 文言」词表**，再对 **789 条唯一英文字符串**做 8 路并行的文言翻译，受风格指南与车万女仆 `lzh.json` 参考约束；键序与 `en_us.json` 完全一致。

#### 内置模型元数据本地化

内置模型的名称/说明/作者等此前只回退到英文（`ModelMetadataPresenter.getLocalizedString` 仅认 `en_us`）。现由 `{en_us, zh_cn}` 扩展至 **15 种语言**：新增 `zh_tw`、`zh_hk`，以及 `es_es`、`fr_fr`、`id_id`、`ja_jp`、`ko_kr`、`pt_br`、`ru_ru`、`tr_tr`、`uk_ua`、`vi_vn`、`lzh`。同时把既有 10 个非中文语言中「缺键或取值仍等于英文」的 **611 条唯一英文字符串**补齐。

- 修复一处真实缺陷：`ru_ru.json` 的 `open_model_folder.tips` 用了西里尔字母 `п`/`а` 冒充拉丁 `n`（`§папка`），破坏原版格式码；已更正。
- 对 11 组「英文相同、中文含义不同」的键逐组判断，改写其中 3 个确实歧义的键。

> 译文经抽样校对，**未经母语者通读**（约 1400 条，覆盖 10 种语言 + 文言）。12 处占位符告警经 `git diff` 确认属既有、无害（死键或参数数量正确），未改动。

#### Paper Doll 拆分与经典 HUD 玩偶选项

把 26.x 分支中 999 行的 `ModelPreviewRenderer.java` 拆为**兼容门面（999 → 177 行，25/25 公开 API 保持、零调用点迁移）**加新的 `client/renderer/preview/` 预览包：`PreviewSceneRenderer`（床/地面/载具预览道具）、`PreviewMath`（刻意不依赖 MC 的投影/鼠标数学，便于纯 JVM 单测）、`PreviewRenderBridge`（GUI 预览队列与 `InventoryScreen` 握手）、`GuiModelRenderer`、`PaperDollRenderer`、`PaperDollLayout`（纯数学，含反解）、`PaperDollVisibilityPolicy`、`PaperDollActivity`。并补充 `PaperDollIsolationTest`、`PaperDollHeadModeContractTest`、`PaperDollLayoutTest`、`PaperDollVisibilityPolicyTest`。

经典 HUD 玩偶新增一组选项（配置分组 `extra_player_render`，**默认值等于历史行为**，即装完不改配置没有任何变化）：

| 选项 | 配置键 | 默认 | 含义 |
|---|---|---|---|
| 头部朝向 | `ClassicHudHeadMode` | `STRAIGHT` | `STRAIGHT` 头随身体（旧行为）；`FOLLOW` 头随真实视角，限位 ±85° |
| 锚点 | `ClassicHudAnchor` | `TOP_LEFT` | 九宫格锚点 + X/Y 偏移；`TOP_LEFT` 加原像素值即精确旧位置 |
| 空闲自动隐藏 | `ClassicHudAutoHideIdleSeconds` | `0`（关） | 空闲 N 秒后隐藏玩偶 |
| 三人称隐藏 | `ClassicHudHideInThirdPerson` | `false` | 第三人称下隐藏 |
| 载具对齐 | `ClassicHudAlignWithVehicle` | `false` | 玩偶朝向与载具对齐 |
| 仅动作时显示 | `ClassicHudShowOnActionOnly` | `false` | 仅在移动/潜行/游泳/滑翔/攀爬/入水/骑乘/使用/攻击/受击/睡眠时显示 |

> 以上选项**仅通过配置文件设置，没有图形设置界面**。

#### 骑乘与选择修复

- **乐魂（快乐恶魂）坐下动作用错**：`LivingMovementAnimationPredicate` 在 1.21.1 用 `Saddleable`（可上鞍生物）判定骑乘，而 26.x 因该 API 移除改为 `vehicle instanceof LivingEntity → 骑乘` 兜底。乐魂 `extends Animal → LivingEntity`，于是被当成「跨骑」载具、套用了马的骑乘姿势。现于兜底**之前**显式识别乐魂并走 `sit`，与本模组 `sit`（坐在实体上）的既有语义一致。仅 26.x 四个分支受影响（1.21.1 无此生物）。
- **为非玩家设置模型会污染玩家自己的选择**：`ModernPlayerModelScreen#applyModelAndTexture` 的自定义目标分支（女仆/NPC）无条件调用 `ClientModelManager.rememberSelectedModel(...)`，该方法会写玩家选择轨并落盘；下次进服时 `restorePersistedModelSelection()` 便把目标实体的模型自动套到玩家身上。现新增 `rememberPlayerSelection` 开关（默认仅玩家自己的面板为 `true`），并开放 `shouldRememberPlayerSelection()` / `setRememberPlayerSelection(boolean)` 供第三方 NPC 面板精确覆写。

#### Blaze3D 帧图通道（实验性，默认关闭）

诊断「开启后多数世界内模型消失」：几何已被成功延迟（日志可见 `[SM-BLAZE3D] ... ACTIVE`）且已从原版收集器移除，但帧图 pass **从未执行**这些绘制，模型既不在收集器也未绘制，因此不可见。已排除配置未生效、mixin 未加载、目标签名缺失、注入点不可达、访问器不匹配、帧图剔除六种可能。

本版不加高权限手段，改为**自愈回退 + 分段诊断**：新增每帧计数器；若某帧延迟了绘制却未全部渲染（pass 未跑、跑迟了或被拒绝），则**本会话内永久自动关闭该通道**并打印丢失几何详情，回退到常规提交路径——使该缺陷至多影响一帧。注册异常同样自愈。

> 该功能的**真实根因尚未确认**，诊断文档的复测区仍在等待用户日志；本版交付的是自愈回退与诊断，**不是已证实的修复**。该通道**默认关闭且属实验性**，需在配置中显式开启。

### 构建与验证

- 六个分支均已 `git push`，并 `fetch` 核对本地 HEAD 与对应远端分支一致；工作树干净。
- 使用发布构建脚本执行干净构建（1.21.1 用 JDK 21，26.x 用 JDK 25），六个分支均产出原版 JAR；CurseForge 变体额外校验不含原生库。
- 六个分支全量单元测试通过，合计 **2204 项、0 失败**（每分支 1 项跳过）：
  Fabric 1.21.1 **365**、Fabric 26.1.2 **386**、Fabric 26.2 **393**、NeoForge 1.21.1 **338**、NeoForge 26.1.2 **357**、NeoForge 26.2 **365**。
- 说明：1.21.1 分支需 **JDK 21**（JDK 25 会使 Gradle 在创建 test 任务时报 `Type T not present`）；26.x 用 JDK 25。1.21.1 是 `common` + `fabric` 双项目结构，测试由 `:common:test` 承载（`:fabric:test` 为 NO-SOURCE）。

### 发布资产

- 六个原版 JAR（Fabric / NeoForge × 1.21.1 / 26.1.2 / 26.2）。
- 中英文双语发布说明（本文件及英文版）。

### 兼容性矩阵

| 加载器 | Minecraft | 分支 | 产物 |
|---|---:|---|---|
| Fabric | 26.2 | `fa26.2` | `sparkle-morpher-1.2.6-fa26.2.jar` |
| Fabric | 26.1.2 | `fa26.1.2` | `sparkle-morpher-1.2.6-fa26.1.x.jar` |
| Fabric | 1.21.1 | `main` | `sparkle-morpher-1.2.6-fa1.21.1.jar` |
| NeoForge | 26.2 | `neo26.2` | `sparkle-morpher-1.2.6-neo26.2.jar` |
| NeoForge | 26.1.2 | `neo26.1.2` | `sparkle-morpher-1.2.6-neo26.1.x.jar` |
| NeoForge | 1.21.1 | `neo1.21.1` | `sparkle-morpher-1.2.6-neo1.21.1.jar` |

请选择与你的加载器及 Minecraft 版本同时匹配的产物。1.21.1 构建需要 1.21.1 的加载器/API 线，请勿与 26.x 安装混用。

### 已知限制

- **Blaze3D 帧图通道**默认关闭、属实验性；真实根因未确认，本版为自愈回退与诊断。
- **经典 HUD 玩偶选项**仅配置文件可设，无图形界面；默认值等于历史行为。
- **纸娃娃快照复用（Slice E）未实现**：26.2 的 `ModernHudRenderer.renderAt` 有意不消费姿态快照，且契约测试要求保留该行；消费世界快照会让复杂模型的骨骼矩阵被二次求值覆盖（发丝/配件/四肢残影）。需要实机复现与只读共享设计。
- **头部俯仰与「跟随两者」朝向未实现**（仅支持偏航跟随）；**旋转解锁（Rotation Unlock）未实现**，需相机/输入层改动，跨模块。
- 1.21.1 分支未做预览包拆分（仅实现相同语义，走 FBO 路径）。
- 卡片模式的观感与低端机帧率（每页至多 8×2=16 个实时预览）尚需实机反馈。
- 1.21.1 分支仍存在一处共享静态投影字段，其取值依赖调用顺序，语义脆弱，尚未调整。
- 1.2.6 的卡片目录相关工作在无头环境完成，未做逐项实机视觉验收。
