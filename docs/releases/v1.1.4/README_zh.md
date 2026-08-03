> [English](https://github.com/sdf123098/Sparkle-Morpher/releases/tag/v1.1.4) | **中文**

# Sparkle Morpher 1.1.4

Sparkle Morpher 1.1.4 版本包含了对车万女仆 (Touhou Little Maid) 专用服务器崩溃问题的彻底修复，以及对多人游戏中远程玩家模型动画与位移流畅度的深度重构与优化。

---

## 🐛 车万女仆 (Touhou Little Maid) 专用服务器崩溃修复

- **彻底消除 `NoClassDefFoundError: LocalPlayer`**：修复在 Dedicated Server 专用服务器上，玩家与车万女仆交互（`mobInteract`）时抛出客户端专用类缺失异常打崩服务器线程的问题。
- **服务端/客户端兼容层严格隔离**：重构模组兼容代码逻辑，将所有依赖 `Minecraft` / `LocalPlayer` / GUI 的方法隔离至客户端专用兼容类（`TouhouLittleMaidClientCompat`），确保服务端可达字节码及常量池达到 **零客户端类引用**。
- **覆盖全变体与加载器**：修复已全面覆盖并同步至 Fabric 1.21.1 / 26.1.x / 26.2 及 NeoForge 1.21.1 / 26.1.x / 26.2 六个构建变体。

---

## ⚡ 远程玩家模型流畅度与动画重构优化

- **消除远程玩家 20Hz 卡顿与跳动**：将异步动画求值的时间基准固定在提交时刻（渲染线程捕获），彻底解决 Worker 线程评估延迟跨越客户端 tick 边界引发的时间相位跃迁与单调性冻结问题。
- **按需渲染评估 (On-Demand Evaluation)**：取消帧首对全部实体的盲目批量提交，改为仅在渲染线程当帧实际渲染可见实体时按需提交 Worker 任务，避免视锥剔除/不可见实体无效消耗线程池资源。
- **远程玩家行走步频与动画平滑插值**：远程玩家默认使用与本地玩家一致的 `walkAnimation` 逐帧位移与速度插值，消除相位超前（腿部摆动过快）与走/停硬阈值切换，使多人游戏中其他玩家的移动与步幅动画自然流畅。

---

## 📢 社区与文档

- 在各语言 README 中新增 Telegram 官方交流群组链接：[https://t.me/sparklemorpher](https://t.me/sparklemorpher)
- 欢迎加入 Telegram 群组与 Discord 社区反馈问题及提交建议！

---

## 📦 包含构建产物 (Release JARs)

- `sparkle-morpher-1.1.4-fa1.21.1.jar` (Fabric 1.21.1 / Java 21)
- `sparkle-morpher-1.1.4-fa26.1.x.jar` (Fabric 26.1.x / Java 25)
- `sparkle-morpher-1.1.4-fa26.2.jar` (Fabric 26.2 / Java 25)
- `sparkle-morpher-1.1.4-neo1.21.1.jar` (NeoForge 1.21.1 / Java 21)
- `sparkle-morpher-1.1.4-neo26.1.x.jar` (NeoForge 26.1.x / Java 25)
- `sparkle-morpher-1.1.4-neo26.2.jar` (NeoForge 26.2 / Java 25)
