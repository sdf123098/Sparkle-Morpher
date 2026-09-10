# Sparkle Morpher 1.2.5

## Player Visual Runtime / 渲染管线与开发者工具

发布日期：2026-09-11
支持分支：Fabric 1.21.1、Fabric 26.1.2、Fabric 26.2、NeoForge 1.21.1、NeoForge 26.1.2、NeoForge 26.2

> [English](RELEASE_NOTES_1.2.5_en-US.md) | **中文**

Sparkle Morpher 1.2.5 完成 1.2.x 计划中的 **Player Visual Runtime** 阶段，新增一条默认关闭的 Blaze3D 帧图渲染通道，并为模型作者补上统一的「开发者选项」面板。六个实际代码仓库均已同步、干净构建并推送到对应 GitHub 分支。

### 主要内容

#### Player Visual Runtime（渲染上下文收口）

把此前散落在 ThreadLocal 与各处布尔标志里的渲染状态，统一收进 `RenderContext`：

- `RenderPass` 新增 `FIRST_PERSON` 与 `PAPER_DOLL`，追加在末尾以保持既有 ordinal 语义不变。
- `RenderContext` 由 `ThreadLocal<RenderPass>` 升级为不可变 record `ThreadLocal<RenderScope>(pass, modelPreview, entity, partialTick)`；旧 API（`enter`/`restore`/`currentPass`/`isGuiPreview`/`isOldHud`）语义不变。
- 第一人称判定收口到 `RenderContext`：删除独立 `FIRST_PERSON_MODE` 字段，保留 PBR 与 FirstPerson 模组的全局兜底。经 worker 线程行为抽查确认安全——worker 线程本就读取默认值，其语义由方法参数传递。
- 模型预览标志并入 `RenderScope`，新增 `isAnyPreview()` 收敛「模型预览 ∪ GUI 预览」的两处并集读取。
- 新增 `PhysicsDomain`（WORLD / PREVIEW / EXTRA_PLAYER / FIRST_PERSON）显式表达物理域。
- 新增 `PlayerFrameSnapshot`（帧级渲染输入 + 可测的 `sanitizePartialTick`）与 `PlayerRenderPolicy`（纯决策，无 Minecraft 依赖，可单测），把渲染门控从调用点抽出为策略输入。

#### 默认关闭的 Blaze3D 帧图渲染通道

新增把 GPU 绘制登记为「延迟绘制」并作为一等帧图 pass 插入 Minecraft 帧图的通道：

```text
提交阶段  IGeoRenderer → 登记（姿态深拷贝）
构建帧图  注入 LevelRenderer 帧图构建尾部，声明 readsAndWrites(main)
MC 调度   Minecraft 负责顺序、barrier 与资源生命周期
```

- 关键发现：玩家阴影由独立提交（仅相对位置与形状，不含模型几何），因此 GPU 蒙皮与阴影并不冲突，无需为阴影牺牲该通道。
- **保守门控（首版只覆盖最普通场景）**：需要配置开关与实验开关同时为真，且满足世界渲染、非预览、非第一人称、无光影包、非发光实体、不透明、无部件遮罩等条件；其余情况全部走既有路径。
- **失败安全**：全链路 `try/catch`，异常只记录一次并清空本帧待绘，帧开始/结束无条件清理。
- 开关使用标准模组配置（`EnableBlaze3DInPipelineDraw`，默认关闭，位于「性能」分组），**不需要任何 JVM 参数**；同时新增两条不受日志门控的一次性 INFO（`[SM-BLAZE3D]`），避免只开启新通道的用户看不到任何输出。
- 同批完成的相邻改进：按 `translucentTexture` 选择半透明管线（#2）；把「后端名等于 VULKAN」的判断改为按能力路由（#3，OpenGL 快路径行为不变）；绘制前预编译两条管线，失败保留惰性编译（#4）。

> 该通道**默认关闭**，属于逐步放开的保守首版；半透明贴图、发光实体、发光骨骼拆分、预览与第一人称场景仍走既有路径。

#### 模型作者「开发者选项」

原「调试」分组升级为「开发者选项」，并按功能二次分类为四个分区——**面板状态 / 日志 / 性能剖析 / 渲染诊断**（渲染诊断仅 26.x 分支存在，因其中四项配置为该版本专属）：

- **面板状态**：导出当前面板状态为 JSON 到剪贴板、重置面板状态；以及一个**默认关闭**的「从剪贴板写回面板状态」功能，用于复现作者反馈的界面状态。写回采用「先全量校验、再全量应用」，任何非法值整批拒绝，需要宿主上下文的确认面板不可写回。
- **日志 / 性能剖析**：补齐两项此前存在但从未挂到界面的开关（网络在线调试日志、重复动画求值告警）。
- 面板状态改为按上下文键缓存，同一目标重开仍记住上次的标签页、搜索与滚动位置，不同目标之间互不串用。

#### 兼容性修复

- **ParCool（NeoForge 三分支）**：此前 NeoForge 侧缺少 loader 实现，导致 ParCool 动作从未参与动画判定。新增纯反射桥接（不引入编译期依赖），映射内置动画 JSON 的 38 个动作名，并按动作实例记忆方向以避免播放中方向漂移；同时提供 `ctrl.parcool_loaded` / `ctrl.parcool_doing` 两个 Molang 变量。Fabric 侧保持空桩（ParCool 为 NeoForge 专用）。
- **拔刀剑 SlashBlade（NeoForge 1.21.1，Issue #25）**：修复攻击动画上下与左右颠倒、刀鞘绑到错误侧且高度不对的问题。根因是在 YSM 实体空间直接套用了原版 layer 空间数学，缺少 `scale(-1,-1,1)` 与垂直基准补偿；同项目的鞘翅与鹦鹉层本已显式补偿，唯独该分支遗漏。同时补上女仆手持拔刀剑的渲染分支。
- **渲染后端决策分支漂移**：neo26.2 / neo26.1.2 的 `RenderBackendDecision` 曾漏同步一次提交，导致 GUI 预览可能落到直绘路径。已按 `isAnyPreview()` 对齐，四个 26.x 分支现逐字一致。

#### 稳定性与其他修复

- **模型面剔除逃生开关**：为「部分模型在某些角度缺面」提供默认关闭的自救入口，覆盖 GPU / Blaze3D / SIMD 全部路径。
- **原生库信任链收敛为 fail-closed**：`YSM_CORE_LIB` 开发覆盖路径此前跳过内容校验，而原生 ABI 版本号在剔除修复前后相同，无法据此识别陈旧库。现默认按清单校验内容哈希，不匹配即拒绝加载并打印期望/实际哈希，另提供显式逃生门；缓存复用与重装均打印清单身份，使「当前实际加载的是哪个原生构建」在日志中可见。
- **Blaze3D 网格资源泄漏修复**：网格缓存从不释放其 GPU 缓冲与逐帧直接内存，且释放路径只在 OpenGL 句柄非零时触发；现补充释放钩子并改为无条件回收。

#### 死代码清理

删除六分支中经全源集核查确认无引用的渲染路径：`PiePortableRenderPath` / `PieMesh` / `PiePipeline` / `PieShader`、`IrisRenderPath`、`BoneXformCompute`，以及随之无用的着色器资源。保留 `Pie` 现役的回退绘制。

### 构建与验证

- 六个分支均已 `git push`，并 `fetch` 核对本地 HEAD 与对应远端分支一致；工作树干净。
- 使用发布构建脚本执行干净构建（1.21.1 用 JDK 21，26.x 用 JDK 25），六个分支均产出原版 JAR；CurseForge 变体额外校验不含原生库。
- 测试：Fabric 26.2 与 NeoForge 26.2 全量测试通过（345 / 317 项，0 失败），包含本版新增的渲染上下文、帧快照、渲染策略、后端路由与帧图 pass 测试。
- 其余分支执行编译与相关测试。

### 发布资产

- 六个原版 JAR（Fabric / NeoForge × 1.21.1 / 26.1.2 / 26.2）。
- 中英文双语发布说明（本文件及英文版）。

### 兼容性矩阵

| 加载器 | Minecraft | 分支 | 产物 |
|---|---:|---|---|
| Fabric | 26.2 | `fa26.2` | `sparkle-morpher-1.2.5-fa26.2.jar` |
| Fabric | 26.1.2 | `fa26.1.2` | `sparkle-morpher-1.2.5-fa26.1.x.jar` |
| Fabric | 1.21.1 | `main` | `sparkle-morpher-1.2.5-fa1.21.1.jar` |
| NeoForge | 26.2 | `neo26.2` | `sparkle-morpher-1.2.5-neo26.2.jar` |
| NeoForge | 26.1.2 | `neo26.1.2` | `sparkle-morpher-1.2.5-neo26.1.x.jar` |
| NeoForge | 1.21.1 | `neo1.21.1` | `sparkle-morpher-1.2.5-neo1.21.1.jar` |

请选择与你的加载器及 Minecraft 版本同时匹配的产物。1.21.1 构建需要 1.21.1 的加载器/API 线，请勿与 26.x 安装混用。

### 已知限制

- 新增的 Blaze3D 帧图通道默认关闭且门控保守，尚未在全部场景（半透明、发光实体、预览、第一人称、光影）下开放。
- 面板状态写回默认关闭，属于面向模型作者的排查工具。
- 1.21.1 分支上存在一处共享的静态投影字段，其取值依赖调用顺序，语义脆弱，尚未调整。
