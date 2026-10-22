package com.micaftic.morpher.client.renderer.hud;

import com.micaftic.morpher.client.gui.HudLayoutScreen;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;

/**
 * 经典 HUD 动画决策：优先复用世界帧姿态；世界不可见时按配置帧率自评估，其余帧保持上一采样姿态。
 */
public final class HudAnimationGate {

    public enum Decision {
        FULL_EVAL,
        REUSE_WORLD_POSE,
        HOLD_SAMPLED_POSE
    }

    private static final boolean REUSE_WORLD_POSE_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("sparklemorpher.hud.reuseWorldPose", "true"));
    private static final int DEFAULT_SAMPLE_FPS = 60;

    private static boolean hasSample;
    private static AnimatableEntity<?> sampledAnimatable;
    private static AnimatedGeoModel sampledModel;
    private static int sampledFps = -1;
    private static long nextSampleNanos;

    private HudAnimationGate() {
    }

    public static Decision decide(AnimatableEntity<?> animatable, AnimationEvent<?> event) {
        if (!RenderContext.isOldHud()
                || animatable.getEntity() != Minecraft.getInstance().player) {
            return Decision.FULL_EVAL;
        }
        // 布局编辑器直接预览编辑中的布局，必须每帧求值，不能套用 HUD 采样节流。
        if (InputUtil.getCurrentScreen() instanceof HudLayoutScreen) {
            return Decision.FULL_EVAL;
        }
        AnimatedGeoModel currentModel = animatable.getCurrentModel();
        float partialTick = event.getPartialTick();
        if (REUSE_WORLD_POSE_ENABLED && HudPoseCache.canReuse(animatable, partialTick)) {
            markSampled(animatable, currentModel);
            return Decision.REUSE_WORLD_POSE;
        }
        int fps = sampleFps();
        long now = System.nanoTime();
        if (fps <= 0) {
            markSampled(animatable, currentModel);
            return Decision.FULL_EVAL;
        }
        if (fps != sampledFps) {
            sampledFps = fps;
            nextSampleNanos = now + sampleInterval(fps);
            markSampled(animatable, currentModel);
            return Decision.FULL_EVAL;
        }
        if (!hasSample || sampledAnimatable != animatable || sampledModel != currentModel) {
            nextSampleNanos = now + sampleInterval(fps);
            markSampled(animatable, currentModel);
            return Decision.FULL_EVAL;
        }
        if (now >= nextSampleNanos) {
            nextSampleNanos = Math.max(now + sampleInterval(fps), nextSampleNanos + sampleInterval(fps));
            return Decision.FULL_EVAL;
        }
        return Decision.HOLD_SAMPLED_POSE;
    }

    private static void markSampled(AnimatableEntity<?> animatable, AnimatedGeoModel model) {
        hasSample = true;
        sampledAnimatable = animatable;
        sampledModel = model;
    }

    private static long sampleInterval(int fps) {
        return 1_000_000_000L / fps;
    }

    private static int sampleFps() {
        try {
            return ExtraPlayerRenderConfig.CLASSIC_HUD_ANIMATION_SAMPLE_FPS != null
                    ? ExtraPlayerRenderConfig.CLASSIC_HUD_ANIMATION_SAMPLE_FPS.get()
                    : DEFAULT_SAMPLE_FPS;
        } catch (Throwable ignored) {
            return DEFAULT_SAMPLE_FPS;
        }
    }
}
