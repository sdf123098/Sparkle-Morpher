package com.micaftic.morpher.client.renderer.hud;

import com.micaftic.morpher.client.animation.debug.AnimationFrameProfiler;
import com.micaftic.morpher.client.entity.GeoEntity;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/**
 * 本帧世界渲染完成后的本地玩家骨骼姿态快照，供 HUD 复用，避免第二次动画求值。
 *
 * <p>快照只在本帧、同一实体/模型/seekTime 下有效；任何一项不匹配都回退到 HUD 自评估。
 * 复制使用静态数组，容量只在模型骨骼数变化时增长。</p>
 */
public final class HudPoseCache {

    private static final float PARTIAL_TICK_EPSILON = 1.0e-3f;
    private static final float SEEK_TIME_EPSILON = 1.0e-3f;

    private static volatile boolean valid;
    private static Entity owner;
    private static AnimatedGeoModel model;
    private static String modelId;
    private static int tickCount;
    private static int renderFrameId = -1;
    private static float partialTick;
    private static float seekTime;
    private static float headBaseX;
    private static float headBaseY;
    private static float[] matrixData = new float[0];
    private static float[] absPivotData = new float[0];
    private static int matrixLength;
    private static int absPivotLength;

    private HudPoseCache() {
    }

    public static void publish(AnimatableEntity<?> animatable, float worldPartialTick) {
        Entity entity = animatable.getEntity();
        if (entity == null
                || entity != Minecraft.getInstance().player
                || RenderContext.isGuiPreview()
                || RenderContext.isModelPreview()
                || !(animatable instanceof LivingAnimatable<?> living)) {
            return;
        }
        AnimatedGeoModel currentModel = animatable.getCurrentModel();
        if (currentModel == null
                || animatable.getLastAnimationEvaluationFrameId() != AnimationFrameProfiler.getRenderFrameId()
                || animatable.getLastAnimationEvaluationModel() != currentModel
                || Float.compare(animatable.getLastAnimationEvaluationSeekTime(), animatable.getSeekTime()) != 0) {
            return;
        }
        float[] sourceMatrix = currentModel.getMatrixData();
        float[] sourcePivot = currentModel.getAbsPivotData();
        if (sourceMatrix.length == 0) {
            return;
        }
        if (matrixData.length != sourceMatrix.length) {
            matrixData = new float[sourceMatrix.length];
        }
        if (absPivotData.length != sourcePivot.length) {
            absPivotData = new float[sourcePivot.length];
        }
        System.arraycopy(sourceMatrix, 0, matrixData, 0, sourceMatrix.length);
        System.arraycopy(sourcePivot, 0, absPivotData, 0, sourcePivot.length);
        matrixLength = sourceMatrix.length;
        absPivotLength = sourcePivot.length;
        owner = entity;
        model = currentModel;
        modelId = modelIdOf(animatable);
        tickCount = entity.tickCount;
        renderFrameId = AnimationFrameProfiler.getRenderFrameId();
        partialTick = sanitizePartialTick(worldPartialTick);
        seekTime = animatable.getSeekTime();
        headBaseX = living.getHeadBaseRotationX();
        headBaseY = living.getHeadBaseRotationY();
        valid = true;
    }

    public static boolean canReuse(AnimatableEntity<?> animatable, float hudPartialTick) {
        if (!valid || renderFrameId != AnimationFrameProfiler.getRenderFrameId()) {
            return false;
        }
        Entity entity = animatable.getEntity();
        AnimatedGeoModel currentModel = animatable.getCurrentModel();
        return entity != null
                && entity == owner
                && currentModel != null
                && currentModel == model
                && Objects.equals(modelIdOf(animatable), modelId)
                && entity.tickCount == tickCount
                && Math.abs(sanitizePartialTick(hudPartialTick) - partialTick) <= PARTIAL_TICK_EPSILON
                && Math.abs(animatable.getSeekTime() - seekTime) <= SEEK_TIME_EPSILON
                && currentModel.getMatrixData().length == matrixLength
                && currentModel.getAbsPivotData().length == absPivotLength;
    }

    public static boolean applyTo(AnimatableEntity<?> animatable) {
        AnimatedGeoModel currentModel = animatable.getCurrentModel();
        if (!valid || currentModel == null || renderFrameId != AnimationFrameProfiler.getRenderFrameId()
                || animatable.getEntity() != owner || currentModel != model
                || !Objects.equals(modelIdOf(animatable), modelId)
                || currentModel.getMatrixData().length != matrixLength
                || currentModel.getAbsPivotData().length != absPivotLength) {
            return false;
        }
        System.arraycopy(matrixData, 0, currentModel.getMatrixData(), 0, matrixLength);
        System.arraycopy(absPivotData, 0, currentModel.getAbsPivotData(), 0, absPivotLength);
        return true;
    }

    public static float headBaseX() {
        return headBaseX;
    }

    public static float headBaseY() {
        return headBaseY;
    }

    public static void invalidate() {
        valid = false;
        renderFrameId = -1;
        owner = null;
        model = null;
        modelId = null;
    }

    private static float sanitizePartialTick(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Mth.clamp(value, 0.0f, 1.0f);
    }

    private static String modelIdOf(AnimatableEntity<?> animatable) {
        return animatable instanceof GeoEntity<?> geo ? geo.getModelId() : null;
    }
}
