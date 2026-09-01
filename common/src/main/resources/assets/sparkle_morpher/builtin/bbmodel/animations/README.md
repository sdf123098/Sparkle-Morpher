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
  的状态名一致：`idle` / `walk` / `run` / `fly` / `elytra_fly` / `swim` / `swim_stand` /
  `sneak` / `sneaking` / `jump` / `sleep` / `riptide` / `death` / `attacked` / `climb` /
  `climbing` / `ladder_up` / `ladder_down` / `ladder_stillness`。另含物品使用/挥舞的
  通用手臂兜底：`use_mainhand` / `use_offhand` / `swing_hand` / `swing_offhand` /
  `attack_empty`（模型自带动画优先，`computeIfAbsent` 不覆盖）。
- `fp.arm.animation.json` — 第一人称手臂动画（进入 `fp_arm` 条目，注入 arm 表）：
  `parallel0-7` 并行槽 + 武器手臂兜底 `hold_mainhand` / `hold_offhand` /
  `use_mainhand` / `use_offhand` / `swing_hand` / `swing_offhand` / `attack_empty`，
  由第一人称自动武器槽（`fp.arm.weapon`，仅 bbmodel/figura 生效）按动作播放。
- `extra.animation.json` — 通用轮盘表情（`extra0` / `extra1` / ...）。

## 重要设计

- 使用 `computeIfAbsent` 注入，**不覆盖** 模型自带同名动画。
- **未作者化的状态会自动回退到 vanilla 姿态兜底**（`ImportedVanillaPoseController`
  fallbackOnly），因此本预设只增不减，不会让任何状态相较改动前倒退。
- 物品的 hold/use/swing 由本预设的通用手臂动画兜底（主模型侧与第一人称 `fp.arm` 侧），
  模型未定义对应动画时生效。

## 现状

当前为 **vanilla 规范骨骼简版占位内容**（19 个状态动画 + 5 个武器手臂兜底 + fp.arm
并行槽与武器兜底），覆盖 `PlayerActionState` 全部基础动作与第一人称手臂持有/使用/挥舞，
用于验证并打通整条流水线。丰富、精修的动作将由社区/原创内容逐步替换补充
（替换同名 JSON 即可，见 方案 · 任务 a）。
