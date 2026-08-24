package com.micaftic.morpher.client.renderer.modernhud;

import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 现代 HUD 共享姿态快照（阶段 1，计划书 §4.1）。
 *
 * <p>不可变、帧作用域：由世界玩家动画评估完成点（{@code CustomPlayerRenderer.render}
 * 内 {@code renderEntityWithTexture} 之后）发布，现代 HUD 只消费本快照进行绘制，
 * 不进行第二次 MoLang / 动画控制器求值。
 *
 * <p>{@code matrixData} / {@code absPivotData} 是 generation-stable 的数组引用
 * （归属 {@link AnimatedGeoModel}，由世界帧动画评估写入）。同帧消费安全；
 * 跨帧消费必须由调用方以 {@code modelId} + {@code tick} 校验 freshness，否则回退经典 HUD。
 *
 * <p>26.x 移植：{@code ResourceLocation} → {@code Identifier}，其余与 1.21.1 一致。
 */
public final class PlayerPoseSnapshot {

    private final long renderFrameId;
    private final int tick;
    private final float partialTick;
    private final String modelId;
    private final AnimatedGeoModel model;
    private final float[] matrixData;
    private final float[] absPivotData;
    private final Identifier texture;
    private final float bodyRot;
    private final float netHeadYaw;
    private final float headPitch;
    private final int renderPartMask;
    private final boolean hurtOverlay;
    private final float modelWidth;
    private final float modelHeight;

    public PlayerPoseSnapshot(
            long renderFrameId,
            int tick,
            float partialTick,
            String modelId,
            AnimatedGeoModel model,
            float[] matrixData,
            @Nullable float[] absPivotData,
            Identifier texture,
            float bodyRot,
            float netHeadYaw,
            float headPitch,
            int renderPartMask,
            boolean hurtOverlay,
            float modelWidth,
            float modelHeight
    ) {
        this.renderFrameId = renderFrameId;
        this.tick = tick;
        this.partialTick = partialTick;
        this.modelId = modelId;
        this.model = model;
        this.matrixData = matrixData;
        this.absPivotData = absPivotData;
        this.texture = texture;
        this.bodyRot = bodyRot;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
        this.renderPartMask = renderPartMask;
        this.hurtOverlay = hurtOverlay;
        this.modelWidth = modelWidth;
        this.modelHeight = modelHeight;
    }

    public long renderFrameId() {
        return renderFrameId;
    }

    public int tick() {
        return tick;
    }

    public float partialTick() {
        return partialTick;
    }

    public String modelId() {
        return modelId;
    }

    public AnimatedGeoModel model() {
        return model;
    }

    /** 骨骼动画参数（世界帧评估结果引用）。 */
    public float[] matrixData() {
        return matrixData;
    }

    /** 每帧骨骼状态（pivot 等，可能为 null）。 */
    @Nullable
    public float[] absPivotData() {
        return absPivotData;
    }

    public Identifier texture() {
        return texture;
    }

    /** 快照可渲染判定：模型与骨骼数据有效（阶段 2 提交前置）。 */
    public boolean isRenderable() {
        return model != null && matrixData != null;
    }

    public float bodyRot() {
        return bodyRot;
    }

    public float netHeadYaw() {
        return netHeadYaw;
    }

    public float headPitch() {
        return headPitch;
    }

    /** 渲染 part mask（0 = 全部骨骼）。 */
    public int renderPartMask() {
        return renderPartMask;
    }

    public boolean hurtOverlay() {
        return hurtOverlay;
    }

    /** 模型外接尺寸（自动 HUD framing 用，阶段 2 起消费）。 */
    public float modelWidth() {
        return modelWidth;
    }

    public float modelHeight() {
        return modelHeight;
    }
}
