> [English](https://github.com/sdf123098/Sparkle-Morpher/releases/tag/v1.2.1) | **简体中文**

# Sparkle Morpher 1.2.1

Sparkle Morpher 1.2.1 修复了 Fabric 26.x 分支的单机进图崩溃，将 26.2 分支对齐到真实 26.2 API，并把 2026-08-10 至 08-13 的渲染/网络/Maid 重构系列 R0–R11（含 08-12 预发布 `1.2.0-beta-R11MAID` 全部内容）以正式版形式发布到 Fabric / NeoForge × 1.21.1 / 26.1.2 / 26.2 六个分支。

---

## 主要更新

### 崩溃修复（本次重点）

- **修复单机进图 `IncompatibleClassChangeError`**（Fabric 26.1.2 / 26.2）：移除编译期影子 stub——`Minecraft.execute/submit/isLocalServer` 曾被声明为 static，导致 `Minecraft.getInstance().execute(...)` 被编译成 `invokestatic`，运行时解析到真实实例方法即崩溃。现已全部重新编译为 `invokevirtual`（ASM 全量扫描验证 0 处残留）。
- **Fa26.2 stub 清理与真实 26.2 API 对齐**：删除 24 个残留/遮蔽 stub，仅保留 4 个运行时兼容层（`MultiBufferSource` / `IrisApi` / `ModConfig` / `IForgeGuiGraphicsExtractor`）；`getScoreboard` / `renderNames` / `renderBuffers` / `getMainRenderTarget` / `screen` 等 6 处调用点改到真实 26.2 API。
- **同步包队列溢出 → 有限背压**：大模型库/高带宽突发时不再因队列瞬时打满直接中止整个同步，改为入队侧有限等待（服务端带宽限流 + 出站缓冲排水形成背压闭环）。

### 渲染与性能

- **经典 HUD 深度性能重构**：FBO 局部物理像素离屏缓存（原每帧 CPU 全量渲染约 35 ms → 约 0.17 ms）；GPU 路径 60 Hz 独立刷新预算、SIMD/兼容路径 10 Hz 节流；8 槽骨骼 SSBO 环形缓冲 + `GL_STREAM_DRAW` 流式上传，消除共享缓冲同步等待；显式 Alpha 合成与批处理隔离。
- **第一人称手部空白修复**（26.x）：手部渲染恒用 `entityTranslucent` 通道。
- **预览旋转污染修复**（1.21.1）：异常路径也复位实体 yaw 与预览模式（try/finally）。
- **手部渲染 NPE 防御**：`getAnimationBundle()==null` 中间态兜底（六分支 12 处）。
- **R10 系列内部重构**：`RenderBackend` 接口隔离（Blaze3D / OpenGL / SIMD / Java 四实现）、`GpuMeshRegistry` 租约 + 孤儿回收、模型装配统一资源 ownership（确定性释放）、音频缓存加权 LRU。

### 网络与模型同步

- **未装 SPM 客户端连服崩溃修复**：发送前逐玩家通道预检（NeoForge / Fabric）+ 批量发送兜底。
- **同步超时 / 卡"正在接收模型数据"系统性修复**：`LegacySyncFlowControl` 64 KiB 突发背压 + 512 MiB 在途预算；专用单线程同步执行器 + 有界顺序队列（解密/校验/落盘不再占用网络/主线程）；服务端缓存缺失时发送终止帧，客户端不再永久挂起；看门狗不再伪报成功。
- **neo1.21.1 卡 LOADING 修复**：`toClientboundPacket` 空包 → `ClientboundCustomPayloadPacket` 包装。
- **R9 网络模块化**（内部）：发送入口预检下沉、Connection state 拆分、上传传输接口化。

### Maid / TLM 女仆兼容（R11）

- `MaidModelSync` 分支分叉收敛（六分支字节级一致）。
- **C2SSetMaidModelPacket（id 24）全分支移植**：女仆换模统一走 SPM 自有链路（auth 授权链），官方 TLM 协议保留为回退。
- TLM 环境启动崩溃修复（`@Mixin(targets=...)` 规避 `MixinTargetAlreadyLoaded`）。
- 26.x GUI 女仆预览未替换 YSM 模型修复。
- R11 compat.api 服务化（内部，无启动顺序依赖）。

### GUI / HUD

- **经典 / 现代 HUD 双开关**：设置页独立开关，删除旧快捷键与旧界面。
- **`ClassicHudLayoutScreen` 布局编辑器**：拖动、无级缩放、滚轮缩放、yaw 旋转、一键重置。
- **`ModernHudRenderer` 独立入口契约**（默认关闭，内部）。

### 其他修复

- 骑乘状态头部异常旋转（`RiderRotationMath` 纯角度辅助类，六分支验证）。
- 1.21.1 默认模型整体旋转不跟随视角（跨帧缓存注册表保留）。
- 服务器模型包名 JSON 引号显示修复。

### 内部架构重构（R0–R9，1.2.1 核心）

- S0 安全热修：YSM folder 路径逃逸沙箱、音频缓存原子去重。
- R2 线程池统一（`SmExecutors` 有界队列 + CallerRuns 背压 + TaskScope）。
- R3 `ModelStoragePaths` 路径集中 + `PersistentStore` 原子写。
- R4 资源容器三源统一读取（folder / zip GBK / 限额）+ zip bomb 防护。
- R5 Model Domain / R6 EntityModelResolver（优先级 + revision 竞态防护）。
- R7 拆分 `ClientModelManager`（3024 行 → 5 个职责类）。
- R8 拆分 `ServerModelManager`（1899 行 → 8 个职责类，原子文件移动 + 上传策略）。
- **测试从 45 个 → 201 个/分支**（YsmCrypt golden vectors、模型 corpus、竞态验收）。

---

## 文件（原版全量构建）

| 文件 | 平台 |
|---|---|
| `sparkle-morpher-1.2.1-fa1.21.1.jar` | Fabric 1.21.1 |
| `sparkle-morpher-1.2.1-fa26.1.x.jar` | Fabric 26.1.2 |
| `sparkle-morpher-1.2.1-fa26.2.jar` | Fabric 26.2 |
| `sparkle-morpher-1.2.1-neo1.21.1.jar` | NeoForge 1.21.1 |
| `sparkle-morpher-1.2.1-neo26.1.x.jar` | NeoForge 26.1.2 |
| `sparkle-morpher-1.2.1-neo26.2.jar` | NeoForge 26.2 |

> CurseForge 版（不含 natives）见 Curseforge 目录，未随本 release 上传。
