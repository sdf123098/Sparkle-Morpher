# bbmodel 专属动作动画预设

这个目录存放 **bbmodel / figura 导入模型专用** 的内建动作动画，由 `BuiltinBbmodelActionPreset` 加载。

与 YSM 原生预设（`builtin/default/` 与 `builtin/external_ysm/`）**完全隔离**：
不同目录、不同加载器、不同缓存，内容互不复用。

## 骨骼命名

动画以 **vanilla 玩家骨骼** 作者化，使用如下规范名（大小写不敏感，运行时会归一化）：

`Head` / `Body`（含 `waist`/`torso`/`upperbody` 同义名）/ `LeftArm` / `RightArm` /
`LeftLeg` / `RightLeg`，可选 `LeftForeArm` / `RightForeArm` / `LeftHand` / `RightHand` /
`LeftItem`(→手部定位) / `RightItem`。

注入时 `ModelAssemblyFactory` 会通过 `SemanticSkeleton` 把这些规范名重映射到目标模型的实际骨骼名。

## 文件

- `main.animation.json` — 基础游戏动作（状态驱动）。动画名必须与 `AnimationRegister`
  的状态名一致：`fly` / `elytra_fly` / `swim` / `swim_stand` / `sneak` / `sneaking` /
  `jump` / `sleep` / `riptide` / `death` / `attacked` / `climb` / `climbing` /
  `ladder_up` / `ladder_down` / `ladder_stillness` 等。
- `extra.animation.json` — 通用轮盘表情（`extra0` / `extra1` / ...）。

## 重要设计

- 使用 `computeIfAbsent` 注入，**不覆盖** 模型自带同名动画。
- **未作者化的状态会自动回退到 vanilla 姿态兜底**（`ImportedVanillaPoseController`
  fallbackOnly），因此本预设只增不减，不会让任何状态相较改动前倒退。
- 物品的 hold/use/swing 目前仍由 vanilla 姿态兜底提供；后续可在此补对应动画覆盖。

## 现状

当前文件为 **起步占位内容**（sneak/sneaking/jump/fly/swim_stand + 一个 wave 表情），
用于验证整条流水线。丰富、精修的动作将由社区/原创内容逐步替换补充（见 方案 · 任务 a）。
