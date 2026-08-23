# Sparkle Morpher 1.2.2

## 跨后端性能与稳定性 / 核心现代化系列

发布日期：2026-08-23
支持分支：Fabric 1.21.1、Fabric 26.1.2、Fabric 26.2、NeoForge 1.21.1、NeoForge 26.1.2、NeoForge 26.2

> [English](RELEASE_NOTES_1.2.2_en-US.md) | **中文**

Sparkle Morpher 1.2.2 完成了 1.2.x 重构计划中“跨后端性能与稳定化”阶段的主要落地工作。六个实际代码仓库均已同步、构建并推送到对应 GitHub 分支。

### 主要内容

- Modern HUD 现代化与稳定性修复：
  - 26.x 使用显式 VAO、SSBO、BoneSkinShader 与 144-byte/bone 数据布局，统一骨骼矩阵路径。
  - 恢复透明材质的独立混合阶段和 alpha 处理，修复模型透明度表现。
  - 修复布局锚点、AABB/FBO framing、glow bone 排除、姿态快照与布局缩放/偏航问题。
  - 修复经典 HUD 的 `input_vertical` 方向输入污染，以及世界模型 deferred submit 重放时上下文丢失问题。
  - 所有现代 HUD 的渲染、布局和标题选项均标记为“实验性”；当前默认关闭现代 HUD、默认保留经典 HUD。

- Minecraft 26.1.2/26.2 手持物崩溃修复：
  - 26.x Modern HUD 改用 Mojang GUI item RenderState 提取 API 绘制主手和副手物品。
  - 移除错误的 `ItemEntityRenderState` 手工创建路径，避免 GUI deferred/Picture-in-Picture 阶段将物品提交给没有 renderer 的 `EntityRenderDispatcher`。
  - 保留主手、副手、Scale、Yaw 和 HUD Layout 功能；1.21.1 继续使用其对应的旧版 API。

- 跨后端绘制与性能：
  - Roulette 第一阶段改为缓存几何并通过 `GuiGraphicsExtractor.fill` 走 GUI 提取路径，避免提取阶段直接提交 raw GL/CommandEncoder 后被覆盖；保留 CPU fallback 以保证 OpenGL/Vulkan 可见性一致。
  - 26.2 Vulkan 路径依据实际 Minecraft `GpuDevice` 检测后启用，接入 144-byte/bone Vulkan ABI 和 Native SIMD 骨骼计算；非目标后端回退到 Java 路径。
  - 继续保持后端中立的 Blaze3D/原版绘制边界，未将业务逻辑绑定到 raw OpenGL/Vulkan。

- 兼容性与可靠性：
  - 修复 TouhouLittleMaid 的 sitting 状态读取及自定义模型状态同步，覆盖六个 loader/version 分支。
  - 修复 1.21.1 物品 buffer/glint immediate path（Issue #18）以及 Fabric 26.x 飞行动画帧边界抖动。
  - 补充架构边界、RenderState 合约、材质合约和分支构建检查；保留 1.21.1 与 26.x 两套必要的版本适配。

### 构建与验证

- 六个分支均已执行 `git push`，并 fetch 核对本地 HEAD 与对应远端分支一致。
- 2026-08-23 完成干净发布构建，六个分支均生成 1.2.2 原版 JAR；发布目录中的六个 JAR 是本次 Release 的主下载文件。
- 详细构建输出随附于 `BUILD_LOG_1.2.2_2026-08-23.txt`，分支日志来自 `.buildlogs/6branches/`。
- 本版本未宣称已完成真实 Minecraft 26.2 Vulkan 客户端视觉回归；该项仍需要实际 Vulkan 客户端环境验证。Fabric 26.2 的完整 ArchitectureBoundaryTest 还存在既有边界违规，未将其冒充为本次修复结果。

### 发布资产

- 六个 Fabric/NeoForge 原版 JAR。
- 本中文说明、英文说明和构建日志。

本版本继续遵循 1.2.2 范围冻结：大型 Action Runtime、Paper Doll、Import Pipeline、Cloud 以及 PortableGpuRenderPath 等后续重构不包含在本次发布中。
