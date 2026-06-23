package com.micaftic.morpher.client.animation.debug;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.entity.GeoEntity;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class AnimationFrameProfiler {

    private static final Long2IntOpenHashMap EVALUATIONS_THIS_FRAME = new Long2IntOpenHashMap();

    private static volatile int renderFrameId;

    private AnimationFrameProfiler() {
    }

    public static boolean isEnabled() {
        return GeneralConfig.safeGet(GeneralConfig.ANIMATION_FRAME_PROFILER, false);
    }

    public static void beginRenderFrame(float partialTick) {
        renderFrameId++;
        EVALUATIONS_THIS_FRAME.clear();
        if (isEnabled() && GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false)) {
            YesSteveModel.LOGGER.info("[SM-ANIM] frame={} partialTick={}", renderFrameId, partialTick);
        }
    }

    public static int getRenderFrameId() {
        return renderFrameId;
    }

    public static void logReusedEvaluation(AnimatableEntity<?> animatable, AnimationEvent<?> event, float seekTime) {
        if (!isEnabled() || !GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false)) {
            return;
        }
        YesSteveModel.LOGGER.info("[SM-ANIM] reuse frame={} entity={} model={} tick={} partial={} frameTime={} seekTime={}",
                renderFrameId,
                animatable.getEntity().getId(),
                getModelId(animatable),
                event.getTickCount(),
                event.getPartialTick(),
                event.getFrameTime(),
                seekTime);
    }

    public static void logReuseMiss(AnimatableEntity<?> animatable, AnimationEvent<?> event, String reason, float seekTime, float previousSeekTime, boolean animationActive, boolean previousAnimationActive, int boneCount, int previousBoneCount, int controllerCount, int previousControllerCount) {
        if (!isEnabled() || !GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false)) {
            return;
        }
        YesSteveModel.LOGGER.info("[SM-ANIM] reeval frame={} entity={} model={} reason={} tick={} partial={} frameTime={} seekTime={} prevSeekTime={} active={} prevActive={} bones={} prevBones={} controllers={} prevControllers={}",
                renderFrameId,
                animatable.getEntity().getId(),
                getModelId(animatable),
                reason,
                event.getTickCount(),
                event.getPartialTick(),
                event.getFrameTime(),
                seekTime,
                previousSeekTime,
                animationActive,
                previousAnimationActive,
                boneCount,
                previousBoneCount,
                controllerCount,
                previousControllerCount);
    }

    public static Scope beginEvaluation(AnimatableEntity<?> animatable, AnimationEvent<?> event, float renderTickTime, float seekTime, boolean animationActive, boolean firstPersonPass, int boneCount, int controllerCount) {
        if (!isEnabled()) {
            return null;
        }
        Entity entity = animatable.getEntity();
        int entityId = entity.getId();
        long evaluationKey = getEvaluationKey(entityId, animatable, firstPersonPass, boneCount, controllerCount);
        int count = EVALUATIONS_THIS_FRAME.addTo(evaluationKey, 1) + 1;
        return new Scope(
                System.nanoTime(),
                renderFrameId,
                count,
                entityId,
                getModelId(animatable),
                event.getTickCount(),
                event.getPartialTick(),
                event.getFrameTime(),
                renderTickTime,
                seekTime,
                animationActive,
                firstPersonPass,
                boneCount,
                controllerCount
        );
    }

    public static void endEvaluation(@Nullable Scope scope) {
        if (scope == null) {
            return;
        }
        long costNanos = System.nanoTime() - scope.startNanos;
        boolean repeated = scope.evaluationCount > 1;
        if (repeated && GeneralConfig.safeGet(GeneralConfig.WARN_REPEATED_ANIMATION_EVALUATION, true)) {
            YesSteveModel.LOGGER.warn("[SM-ANIM] repeated evaluation frame={} entity={} model={} count={} costMs={}",
                    scope.renderFrameId, scope.entityId, scope.modelId, scope.evaluationCount, toMillis(costNanos));
        }
        if (GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false)) {
            YesSteveModel.LOGGER.info("[SM-ANIM] eval frame={} entity={} model={} tick={} partial={} frameTime={} renderTick={} seekTime={} active={} firstPerson={} repeated={} evalCount={} bones={} controllers={} costMs={}",
                    scope.renderFrameId,
                    scope.entityId,
                    scope.modelId,
                    scope.tickCount,
                    scope.partialTick,
                    scope.frameTime,
                    scope.renderTickTime,
                    scope.seekTime,
                    scope.animationActive,
                    scope.firstPersonPass,
                    repeated,
                    scope.evaluationCount,
                    scope.boneCount,
                    scope.controllerCount,
                    toMillis(costNanos));
        }
    }

    public static void logControllerState(AnimatableEntity<?> animatable, AnimationEvent<?> event, float seekTime) {
        if (!isEnabled() || !GeneralConfig.safeGet(GeneralConfig.ANIMATION_DEBUG_LOG, false) || event.isFirstPerson()) {
            return;
        }
        StringBuilder controllers = new StringBuilder();
        for (IAnimationController<?> controller : animatable.getAnimationData().getAnimationControllers()) {
            if (!controllers.isEmpty()) {
                controllers.append(" | ");
            }
            controllers.append(controller.getName()).append('=').append(controller.getCurrentAnimation());
        }
        if (animatable instanceof PlayerCapability playerCapability) {
            YesSteveModel.LOGGER.info("[SM-ANIM] state frame={} entity={} model={} tick={} partial={} seekTime={} limbSwing={} limbSwingAmount={} moving={} renderState={} rsWalkSpeed={} rsWalkPos={} controllers=[{}]",
                    renderFrameId,
                    animatable.getEntity().getId(),
                    getModelId(animatable),
                    event.getTickCount(),
                    event.getPartialTick(),
                    seekTime,
                    event.getLimbSwing(),
                    event.getLimbSwingAmount(),
                    event.isMoving(),
                    playerCapability.hasRenderState(),
                    playerCapability.getRenderStateWalkAnimationSpeed(),
                    playerCapability.getRenderStateWalkAnimationPos(),
                    controllers);
        } else {
            YesSteveModel.LOGGER.info("[SM-ANIM] state frame={} entity={} model={} tick={} partial={} seekTime={} limbSwing={} limbSwingAmount={} moving={} controllers=[{}]",
                    renderFrameId,
                    animatable.getEntity().getId(),
                    getModelId(animatable),
                    event.getTickCount(),
                    event.getPartialTick(),
                    seekTime,
                    event.getLimbSwing(),
                    event.getLimbSwingAmount(),
                    event.isMoving(),
                    controllers);
        }
    }

    private static float toMillis(long nanos) {
        return nanos / 1_000_000.0f;
    }

    private static String getModelId(AnimatableEntity<?> animatable) {
        return animatable instanceof GeoEntity<?> geoEntity ? geoEntity.getModelId() : "-";
    }

    private static long getEvaluationKey(int entityId, AnimatableEntity<?> animatable, boolean firstPersonPass, int boneCount, int controllerCount) {
        long key = Integer.toUnsignedLong(entityId);
        key = key * 31L + Integer.toUnsignedLong(System.identityHashCode(animatable));
        key = key * 31L + (firstPersonPass ? 1L : 0L);
        key = key * 31L + Integer.toUnsignedLong(boneCount);
        key = key * 31L + Integer.toUnsignedLong(controllerCount);
        return key;
    }

    public record Scope(long startNanos,
                        int renderFrameId,
                        int evaluationCount,
                        int entityId,
                        String modelId,
                        int tickCount,
                        float partialTick,
                        float frameTime,
                        float renderTickTime,
                        float seekTime,
                        boolean animationActive,
                        boolean firstPersonPass,
                        int boneCount,
                        int controllerCount) {
    }
}
