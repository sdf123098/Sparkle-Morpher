# Caustica `entityTranslucentEmissive` 修复计划

## 已确认的现象

Minecraft 26.2 的 `entityTranslucentEmissive` 使用带 `EMISSIVE` define 的实体管线。该管线跳过
lightmap，但保留纹理 alpha 和顶点 alpha 的透明混合语义。Sparkle Morpher 现在会将 `ysmGlow*`
骨骼单独提交到这一 RenderType，因此 Caustica 可以在捕获边界上看到准确的材质语义。

Caustica 当前仅根据 color target 是否启用 blend 来分类 RenderType，因而把普通
`entityTranslucent` 和 `entityTranslucentEmissive` 合并为同一种随机透明材质。透明度仍能控制
命中覆盖率，但命中后的 primitive emission 为 0，导致自发光消失。

## 建议的 Caustica 侧实现

1. 在 `RtEntityCollector.submitModel` 解析 RenderType 的 RenderPipeline shader defines。
2. 将 `EMISSIVE` 作为独立材质语义保存，不能用 RenderType 名称或 Sparkle Morpher 类名判断。
3. 在提交期间把该语义传给 `RtEntityCapture`，为本次提交生成的 primitive 设置 emissive 标志；
   不要用 packed light 猜测，因为原版 emissive shader 本来就会绕过 lightmap。
4. closest-hit 读取该标志时，以实体纹理采样颜色作为辐射颜色。透明度继续走 Caustica 现有的
   stochastic-alpha 覆盖逻辑；其多帧期望值对应原版 alpha 混合亮度。若需要单帧无噪点结果，
   再把纹理 alpha 显式乘入 emission，并独立处理后方透射。
5. PBR emission map 优先级应高于或与 vanilla emissive 语义明确组合，避免覆盖用户提供的 `_s`
   发光通道。建议最终强度为材质 emission 与 vanilla emissive coverage 的可配置组合，而非硬编码。

## 验证矩阵

- `Flandre_Scarlet`：眼部 `ysmGlowLeftEyesBase/ysmGlowRightEyesBase` 在无环境光时发光。
- 纹理 alpha=155 的 12 个像素：累计采样亮度约为 alpha=255 区域的 155/255。
- 普通 `entityTranslucent`（史莱姆/半透明模型）不产生 emission。
- 普通满亮但非 EMISSIVE 的渲染层不会被误判；如需保留 packed-light 兼容，应作为另一条语义。
- 同时存在 `_n/_s` PBR 纹理时，法线、粗糙度和自发光均正常，模型缓存重建后结果不变。
