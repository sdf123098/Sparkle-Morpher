> [English](https://github.com/sdf123098/Sparkle-Morpher/releases/tag/v1.2.0) | **简体中文**

# SparkleMorpher 1.2.0

SparkleMorpher 1.2.0 扩展了 Bedrock 与 Blockbench 模型兼容性，改进模型加载与缓存自愈，并为 Fabric 和 NeoForge 带来一批渲染、动画和交互修复（基于 2026-08-06 至 08-09 的修复记录）。

---

## 主要更新

### 模型与格式兼容性

- **Bedrock 资源直读**：可直接导入裸 `.geo.json` / `*geometry.json` 与 Bedrock 资源包（zip 识别），支持按 geometry identifier 选择几何（大小写不敏感）、多几何与披风等附加几何；补全常见 Bedrock 动画变量、`-this` 表达式与动作名映射。
- **Blockbench 新版 cube 独立旋转**：支持 `minecraft:geometry` 1.12.0+ 的方块级 `element.rotation`（绕方块自身枢轴旋转），mesh 网格与 cube 两条路径统一。
- **Bedrock 风格四肢命名**：`armLeft`/`armRight`/`legLeft`/`legRight` 骨骼语义映射，铠甲部件匹配改为大小写不敏感。
- **bbmodel 动画镜像修复**：Blockbench XYZ 欧拉角转换为渲染端旋转序的等效值，消除多轴动画镜像/错误播放；并补全 `death`/`sleep`/`swim`/`climb`/`climbing`/`attacked` 六个高频状态的动作 fallback。
- **客户端渲染兼容接口**：通过通用生命周期接口向 Caustica、Iris 等可选渲染器提供模型与纹理事件，官方兼容子模块以 nested JAR 内嵌分发，只需安装一个主模组。

### 稳定性与性能

- **模型缓存自愈**：服务端缓存改为原子写入、发送前校验、按源模型即时重建；客户端校验解密/解压后的内容并拒收损坏数据，减少手动清双端缓存的场景。
- **缓存身份不再依赖 mod jar 哈希**：同版本重建/替换 jar 不再触发全量重下（仅版本号变化使缓存失效）。
- **模型文件夹健壮性**：无关文件（说明、图片、视频等）安全忽略，修复大文件被全量读入内存导致的卡顿与 OOM；启动期缓存校验移出关键加载路径（异步化）。
- **轮盘性能**：26.x OpenGL 后端检测修正 + 回退扫描线几何缓存；Vulkan 模式改三角带网格，消除逐像素 CPU 渲染导致的个位数 FPS。
- 修复模型解析并发竞态导致的 `ProcessorPipeline` 空处理器异常与重复日志刷屏（日志按模型去重限频）。
- YSM molang 计算产生 NaN/Inf 时消毒（修复头发/尾巴乱飘、闪烁）；清理 26.2 测试日志噪音。
- 修复平行控制器混合关键帧与过渡点时导致的 GUI 预览崩溃；修复 GUI 预览实体未分配 ID 时的崩溃。
- 修复 1.21.1 打开模型选择界面时的 NPE 崩溃（模型未就绪时访问动画包），并修复内置默认模型在部分启动器下无法加载的问题（union 资源 scheme 识别）。

### 交互与动画修复

- 普通玩家可用 `/ysm model disable` 关闭自己的模型、恢复原版外观；`reload` 与 `set` 仍要求 OP 2。
- 修复轮盘“点 A 播 B”（1.21.1 轮盘动作错位 + 旧版模型 extra 动画解析），以及 26.2 从轮盘设置页按 ESC 返回后中心图标残留的问题。
- 修复生存模式无法切换 YSM 女仆模型（Issue #11，兼容 TLM 26.x 女仆所有权 API 变更）。
- 兼容车万女仆及大型整合包：修复 `MixinTargetAlreadyLoaded` 启动崩溃的根因（Mixin 配置阶段不再通过 `Class.forName` 提前定义实体类），并修复 26.2 女仆选择 YSM 模型后按 ESC 无法退出、女仆不渲染 YSM 模型的问题（适配 26.2 屏幕 API 迁移）。
- 修复原版 26.x 长矛/三叉戟动画命名空间冲突；修复持矛时的循环动画（lance 动作按 `hold_on_last_frame` 处理）。
- 空手左键：撤销空手专属逻辑（模型可通过 `ctrl.swing('mainhand', 'empty')` 自行触发攻击动画），保留持物左键与 spear 输入跨 tick 去重。
- 修复状态动画数组空字符串占位导致上一状态动画残留的问题（静止站立时不再播放行走摆动动画）。
- 26.2 自定义箭：模型登记 `minecraft:arrow` 时，在没有更具体投射物模型的情况下也会应用于各类箭实体。

