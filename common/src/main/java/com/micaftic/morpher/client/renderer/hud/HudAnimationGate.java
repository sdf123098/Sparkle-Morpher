package com.micaftic.morpher.client.renderer.hud;

import com.micaftic.morpher.client.gui.HudLayoutScreen;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;

/**
 * HUD 动画决策：优先复用世界帧姿态；世界不可见时按配置帧率自评估，其余帧恢复上一采样姿态。
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

    private static final HudSampledPose SAMPLED_POSE = new HudSampledPose();
    private static int sampledFps = -1;
    private static long nextSampleNanos;

    private HudAnimationGate() {
    }

    /** A due HUD sample owns its evaluation even if world rendering consumed this tick. */
    public static boolean shouldEvaluate(Decision decision, boolean scheduled) {
        return scheduled || (RenderContext.isOldHud() && decision == Decision.FULL_EVAL);
    }

    public static Decision decide(HostAnimatable<?> animatable, HostActionEvent<?> event) {
        if (!RenderContext.isOldHud()
                || animatable.getEntity() != Minecraft.getInstance().player) {
            return Decision.FULL_EVAL;
        }
        // 布局编辑器直接预览编辑中的布局，必须每帧求值，不能套用 HUD 采样节流。
        if (InputUtil.getCurrentScreen() instanceof HudLayoutScreen) {
            return Decision.FULL_EVAL;
        }
        ModelScenePose currentModel = animatable.getCurrentModel();
        float partialTick = event.getPartialTick();
        if (REUSE_WORLD_POSE_ENABLED && HudPoseCache.canReuse(animatable, partialTick)) {
            return Decision.REUSE_WORLD_POSE;
        }
        int fps = sampleFps();
        long now = System.nanoTime();
        if (fps <= 0) {
            return Decision.FULL_EVAL;
        }
        if (fps != sampledFps) {
            sampledFps = fps;
            nextSampleNanos = now + sampleInterval(fps);
            return Decision.FULL_EVAL;
        }
        if (currentModel == null || !SAMPLED_POSE.matches(animatable, currentModel,
                currentModel.getMatrixData().length, currentModel.getAbsPivotData().length)) {
            nextSampleNanos = now + sampleInterval(fps);
            return Decision.FULL_EVAL;
        }
        if (now >= nextSampleNanos) {
            nextSampleNanos = Math.max(now + sampleInterval(fps), nextSampleNanos + sampleInterval(fps));
            return Decision.FULL_EVAL;
        }
        return Decision.HOLD_SAMPLED_POSE;
    }

    public static void captureSample(HostAnimatable<?> animatable) {
        if (isHudSampleOwner(animatable) && animatable instanceof LivingAnimatable<?> living) {
            captureSample(animatable, living.getHeadBaseRotationX(), living.getHeadBaseRotationY());
        }
    }

    public static void captureSample(HostAnimatable<?> animatable, float headBaseX, float headBaseY) {
        ModelScenePose model = animatable.getCurrentModel();
        if (isHudSampleOwner(animatable) && model != null) {
            SAMPLED_POSE.capture(animatable, model, model.getMatrixData(), model.getAbsPivotData(),
                    headBaseX, headBaseY);
        }
    }

    public static boolean restoreSample(HostAnimatable<?> animatable) {
        ModelScenePose model = animatable.getCurrentModel();
        return isHudSampleOwner(animatable) && model != null
                && SAMPLED_POSE.restore(animatable, model, model.getMatrixData(), model.getAbsPivotData());
    }

    public static float sampleHeadBaseX() { return SAMPLED_POSE.headBaseX(); }
    public static float sampleHeadBaseY() { return SAMPLED_POSE.headBaseY(); }

    public static void invalidate(HostAnimatable<?> animatable) {
        if (SAMPLED_POSE.owns(animatable)) {
            SAMPLED_POSE.invalidate();
            nextSampleNanos = 0;
        }
    }

    private static boolean isHudSampleOwner(HostAnimatable<?> animatable) {
        return RenderContext.isOldHud() && animatable.getEntity() == Minecraft.getInstance().player;
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
