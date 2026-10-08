# CREDITS — 内置动作动画来源

本目录下 `main.animation.json` 中的部分动作动画改编自 **Mojang / Microsoft
原版 Minecraft Bedrock 资源包**（`animations/player.animation.json`）。

## 改编说明（非逐字节复制）

以下动画由 `tools/convert_vanilla_animations.py` 转换而来：
- 动画名重命名为 SparkleMorpher 动作 key（`walk` / `swim` / `sneak` / `sleep` /
  `ride` / `swing_hand`）
- 删除了原版中在本渲染端不生效的骨骼通道（`root` / `cape` / `waist` / 手持定位）
- Molang 动态表达式（`variable.tcos0` 等）已烘焙为数值关键帧，非原版逐帧数据
- 骨骼名规范化为 vanilla 玩家骨骼（`Head` / `Body` / `LeftArm` / `RightArm` /
  `LeftLeg` / `RightLeg`）

来源镜像：
- `https://github.com/ZtechNetwork/MCBVanillaResourcePack`（原版 Bedrock 资源包镜像）
- 原资产归 Mojang / Microsoft 所有（Minecraft EULA）

## Blockbench Workshop 模型

`idle` 动作改编自以下 Blockbench Workshop 免费模型（cc-by，须署名）：

| 模型 | 作者 | license | 链接 | 说明 |
|---|---|---|---|---|
| Skin idle animation | splatty | CC BY | https://blockbenchworkshop.com/model/splatty/skin-animation-idle | 1.5s 呼吸/微点头/摆臂 idle，转 `idle` |
| metro man and idle player animations | kidasap817 | CC BY | https://blockbenchworkshop.com/model/kidasap817/metro-man-and-idle-player-animations | 已下载备选（Player Idle / Player emote metroman），未并入预设 |

转换工具：`tools/convert_bbmodel_animations.py`；骨骼名已归一化为 vanilla 规范名
（`Right Arm`→`RightArm`、`Waist`→`Body`），`Skin` 辅助骨骼已丢弃。
