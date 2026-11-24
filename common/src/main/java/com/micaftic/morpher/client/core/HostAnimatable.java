package com.micaftic.morpher.client.core;

import com.micaftic.morpher.audio.IAudioStreamFactory;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.animation.debug.AnimationFrameProfiler;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.event.ClientTickEvent;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBoneView;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.client.render.RiderRotationMath;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.hud.HudAnimationGate;
import com.micaftic.morpher.client.renderer.hud.HudPoseCache;
import com.micaftic.morpher.core.api.entity.EntityDataBridge;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.util.log.ILogger;
import dev.spm.core.scene.PoseAnimationLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Clean SPM animation owner replacing legacy {@code HostAnimatable}.
 */
public abstract class HostAnimatable<TEntity extends Entity> {

    private static final float WALK_SPEED_THRESHOLD = 0.15f;

    private final HostEntityFrameState<TEntity> positionTracker;
    public final TEntity entity;

    private @Nullable ModelScenePose currentModel;
    private Map<String, List<HostMolangScript>> animationMap = Map.of();
    private final List<HostControllerSlot<?>> controllerSlots = new ArrayList<>();
    private final Map<String, HostControllerSlot<?>> controllerSlotsByName = new LinkedHashMap<>();

    public boolean wasAnimationActiveLastTick;
    public boolean hasUpdatedThisTick;
    public boolean isTickTriggered;
    public boolean wasEvaluatedLastFrame;
    public float seekTime;

    private float startTick = -1.0f;
    private float limbSwingTracked = 0.0f;
    public float lastTick = -1.0f;
    public boolean isFirstFrameAfterReset = true;
    public boolean needsReset = false;
    public boolean modelInitialized = false;
    private float smoothedRemoteSpeed = 0.0f;

    private int lastAnimationEvaluationFrameId = -1;
    private float lastAnimationEvaluationSeekTime = Float.NaN;
    private boolean lastAnimationEvaluationActive;
    private @Nullable ModelScenePose lastAnimationEvaluationModel;
    private int lastAnimationEvaluationBoneCount;
    private int lastAnimationEvaluationControllerCount;
    private boolean lastAnimationEvaluationPreviewMode;
    private boolean lastAnimationEvaluationExtraPlayerMode;
    private float lastAnimationEvaluationHeadPitch = Float.NaN;
    private float lastAnimationEvaluationNetHeadYaw = Float.NaN;

    public final Map<String, HostPlayState.State> animationStates = new LinkedHashMap<>();
    private final HostAnimationProcessor<TEntity> animationProcessor = new HostAnimationProcessor<>(this);
    private final HostRateLimiter rateLimiter = new HostRateLimiter();

    public abstract ResourceLocation getTextureLocation();

    public abstract boolean isModelReady();

    public abstract float getHeightScale();

    public abstract float getWidthScale();

    public @Nullable abstract ClipSpec getAnimation(String name);

    public abstract void registerAnimationControllers();

    public HostAnimatable(TEntity entity) {
        this.entity = entity;
        this.positionTracker = createPositionTracker(entity);
        this.rateLimiter.setRefreshRate(getRefreshRate());
    }

    public void reset() {
        HudPoseCache.invalidate();
        HudAnimationGate.invalidate(this);
        this.currentModel = null;
        this.animationMap = Map.of();
        this.controllerSlots.clear();
        this.controllerSlotsByName.clear();
        this.animationProcessor.reset();
        this.rateLimiter.reset();
        this.positionTracker.reset();
        this.startTick = -1.0f;
        this.limbSwingTracked = 0.0f;
        this.lastTick = -1.0f;
        this.wasAnimationActiveLastTick = false;
        this.hasUpdatedThisTick = false;
        this.isTickTriggered = false;
        this.isFirstFrameAfterReset = true;
        this.needsReset = false;
        this.wasEvaluatedLastFrame = false;
        this.seekTime = 0.0f;
        this.lastAnimationEvaluationFrameId = -1;
        this.lastAnimationEvaluationSeekTime = Float.NaN;
        this.lastAnimationEvaluationActive = false;
        this.lastAnimationEvaluationModel = null;
        this.lastAnimationEvaluationBoneCount = 0;
        this.lastAnimationEvaluationControllerCount = 0;
        this.lastAnimationEvaluationPreviewMode = false;
        this.lastAnimationEvaluationExtraPlayerMode = false;
        this.lastAnimationEvaluationHeadPitch = Float.NaN;
        this.lastAnimationEvaluationNetHeadYaw = Float.NaN;
        this.animationStates.clear();
        this.smoothedRemoteSpeed = 0.0f;
    }

    public HostEntityFrameState<TEntity> createPositionTracker(TEntity entity) {
        return new HostEntityFrameState<>(entity);
    }

    public HostEntityFrameState<TEntity> getPositionTracker() {
        return this.positionTracker;
    }

    public float getSeekTime() {
        return this.seekTime;
    }

    public int getLastAnimationEvaluationFrameId() {
        return this.lastAnimationEvaluationFrameId;
    }

    public float getLastAnimationEvaluationSeekTime() {
        return this.lastAnimationEvaluationSeekTime;
    }

    public @Nullable ModelScenePose getLastAnimationEvaluationModel() {
        return this.lastAnimationEvaluationModel;
    }

    public void addAnimationController(HostControllerSlot<?> controller) {
        this.controllerSlots.add(controller);
        this.controllerSlotsByName.put(controller.getName(), controller);
    }

    public List<HostControllerSlot<?>> getControllerSlots() {
        return Collections.unmodifiableList(this.controllerSlots);
    }

    public @Nullable HostControllerSlot<?> getControllerSlot(String name) {
        return this.controllerSlotsByName.get(name);
    }

    public @Nullable ControllerSpec getControllerSpec(String name) {
        return null;
    }

    public @Nullable com.micaftic.morpher.resource.models.ModelProperties resolveModelProperties() {
        return null;
    }

    protected @Nullable PoseAnimationLibrary resolveAnimationLibrary() {
        return null;
    }

    protected Map<String, HostMolangScript> resolveExtraFunctions() {
        return Map.of();
    }

    public Optional<IAudioStreamFactory> getAudioStreamFactory(String name) {
        return Optional.empty();
    }

    public @Nullable final List<HostMolangScript> getAnimationExpressions(String key) {
        return this.animationMap.get(key);
    }

    public int getTextureIndex() {
        return 0;
    }

    public float getScale() {
        return 0.15f;
    }

    public void setupAnim(float seekTime, boolean firstFrame) {
    }

    public void afterSetupAnim(float seekTime, boolean firstFrame) {
    }

    public final TEntity getEntity() {
        return this.entity;
    }

    public boolean hasCustomTexture() {
        return false;
    }

    public @Nullable ModelBoneView getBone(int id) {
        return this.currentModel != null ? this.currentModel.getBone(id) : null;
    }

    public boolean shouldRenderOverlay() {
        return true;
    }

    public int getRefreshRate() {
        if (!ConfigPolicies.render().animationDistanceLod()) {
            return ClientTickEvent.getRefreshRate();
        }
        var player = Minecraft.getInstance().player;
        if (player != null && player != this.entity) {
            Vec3 pos = player.position();
            if (pos.x != 0.0 || pos.y != 0.0 || pos.z != 0.0) {
                if (!this.isFirstFrameAfterReset) {
                    float dist = player.distanceTo(this.entity);
                    if (dist > 64.0f) return 15;
                    if (dist > 32.0f) return 30;
                }
                return ClientTickEvent.getRefreshRate();
            }
        }
        return ClientTickEvent.getRefreshRate();
    }

    public @Nullable final HostActionEvent<?> processAnimation(float partialTick) {
        return processAnimationImpl(partialTick, ModelPreviewRenderer.isFirstPersonOnRenderThread());
    }

    public @Nullable HostActionEvent<?> processAnimationImpl(float partialTick, boolean firstPerson) {
        return processAnimationImpl(partialTick, -1, firstPerson);
    }

    public @Nullable HostActionEvent<?> processAnimationImpl(float partialTick, int capturedTickCount, boolean firstPerson) {
        if (this.currentModel == null) {
            return null;
        }
        partialTick = sanitizePartialTick(partialTick);
        Entity entity = this.entity;
        LivingEntity livingEntity = entity instanceof LivingEntity le ? le : null;
        PlayerCapability playerCapability = this instanceof PlayerCapability cap ? cap : null;
        int tickCount = this instanceof IPreviewAnimatable
                ? ClientTickEvent.getTickCount()
                : (capturedTickCount >= 0 ? capturedTickCount : entity.tickCount);
        float frameTime = partialTick;
        boolean shouldSit = (entity.isPassenger() && entity.getVehicle() != null
                && EntityDataBridge.shouldRiderSit(entity.getVehicle()))
                || (livingEntity != null && TouhouLittleMaidCompat.isMaidSitting(livingEntity));

        float limbSwingAmount = 0.0f;
        float limbSwing = 0.0f;
        if (!shouldSit && entity.isAlive() && livingEntity != null) {
            boolean renderStateMovementSuppressed = false;
            if (playerCapability != null && playerCapability.hasRenderState()) {
                limbSwingAmount = playerCapability.getRenderStateWalkAnimationSpeed();
                limbSwing = playerCapability.getRenderStateWalkAnimationPos();
            } else {
                limbSwingAmount = livingEntity.walkAnimation.speed(partialTick);
                limbSwing = livingEntity.walkAnimation.position(partialTick);
            }
            if (playerCapability != null && !playerCapability.isLocalPlayerModel()) {
                float physicalSpeed = this.positionTracker.getPhysicalGroundSpeed(entity);
                if (physicalSpeed > WALK_SPEED_THRESHOLD && Math.abs(limbSwingAmount) <= WALK_SPEED_THRESHOLD) {
                    this.smoothedRemoteSpeed += 0.3f * (physicalSpeed - this.smoothedRemoteSpeed);
                    limbSwingAmount = this.smoothedRemoteSpeed;
                    limbSwing = this.seekTime * 0.6662f;
                } else {
                    this.smoothedRemoteSpeed = 0.0f;
                }
                renderStateMovementSuppressed = true;
            }
            if (!renderStateMovementSuppressed && limbSwingAmount <= 1.0E-4f) {
                float movementSpeed = Mth.clamp(this.positionTracker.getGroundSpeed(entity, null), 0.0f, 1.0f);
                if (movementSpeed > 1.0E-4f) {
                    limbSwingAmount = movementSpeed;
                    limbSwing = this.seekTime * 0.6662f;
                }
            }
            if (livingEntity.isBaby()) {
                limbSwing *= 3.0f;
            }
        }

        HostEntityModelData modelData = new HostEntityModelData();
        modelData.isSitting = shouldSit;

        float lerpBodyRot = 0.0f;
        float lerpHeadRot = 0.0f;
        float netHeadYaw = 0.0f;
        if (playerCapability != null && playerCapability.hasRenderState()) {
            modelData.isChild = livingEntity != null && livingEntity.isBaby();
            lerpBodyRot = playerCapability.getRenderStateBodyRot();
            netHeadYaw = playerCapability.getRenderStateNetHeadYaw();
            lerpHeadRot = RiderRotationMath.absoluteHeadYaw(lerpBodyRot, netHeadYaw);
        } else if (livingEntity != null) {
            modelData.isChild = livingEntity.isBaby();
            lerpBodyRot = Mth.rotLerp(partialTick, livingEntity.yBodyRotO, livingEntity.yBodyRot);
            lerpHeadRot = Mth.rotLerp(partialTick, livingEntity.yHeadRotO, livingEntity.yHeadRot);
            netHeadYaw = lerpHeadRot - lerpBodyRot;
        }
        if (shouldSit && (entity.getVehicle() instanceof LivingEntity vehicle)) {
            float vehicleBodyRot = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot);
            RiderRotationMath.LivingVehicleRotation ridingRotation =
                    RiderRotationMath.constrainToLivingVehicle(lerpHeadRot, vehicleBodyRot);
            lerpBodyRot = ridingRotation.bodyYaw();
            netHeadYaw = ridingRotation.relativeHeadYaw();
        }

        modelData.rawHeadPitch = playerCapability != null && playerCapability.hasRenderState()
                ? playerCapability.getRenderStateHeadPitch()
                : Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        modelData.headPitch = -modelData.rawHeadPitch;
        modelData.rawNetHeadYaw = netHeadYaw;
        modelData.netHeadYaw = -Mth.clamp(Mth.wrapDegrees(netHeadYaw), -85.0f, 85.0f);
        modelData.lerpBodyRot = lerpBodyRot;
        modelData.lerpedAge = tickCount + partialTick;

        HostActionEvent<HostAnimatable<TEntity>> event = new HostActionEvent<>(
                this, limbSwing, limbSwingAmount, tickCount, partialTick, frameTime,
                Math.abs(limbSwingAmount) > 1.0E-4f, firstPerson, modelData);
        setCustomAnimations(event);
        return event;
    }

    public void setCustomAnimations(@NotNull HostActionEvent<HostAnimatable<TEntity>> event) {
        float currentTick = event.currentTick;
        boolean active = !shouldSkipAnimation(event);
        if (currentTick > this.lastTick) {
            this.hasUpdatedThisTick = false;
            this.isTickTriggered = false;
            this.lastTick = currentTick;
            this.rateLimiter.setRefreshRate(getRefreshRate());
            this.isFirstFrameAfterReset = this.needsReset;
            this.needsReset = false;
        } else {
            currentTick = this.lastTick;
        }
        if (this.startTick == -1.0f) {
            this.startTick = currentTick;
        } else {
            float elapsed = currentTick - this.startTick;
            float delta = elapsed - this.limbSwingTracked;
            if (delta > 0.0f) {
                this.limbSwingTracked = elapsed;
                this.seekTime += delta;
            }
        }
        event.currentTick = this.seekTime;
        this.positionTracker.updateState(event.getTickCount(), this.seekTime, event.getFrameTime());

        if (!this.animationProcessor.isDisabled()) {
            this.isTickTriggered |= this.rateLimiter.request(this.seekTime / 20.0f);
            boolean evaluate = (this.isTickTriggered && !this.hasUpdatedThisTick) || this.wasAnimationActiveLastTick || active;
            boolean firstStep = (!active || (this.seekTime == 0.0f && !this.hasUpdatedThisTick)) && this.isTickTriggered && !this.hasUpdatedThisTick;
            HudAnimationGate.Decision hudDecision = HudAnimationGate.decide(this, event);
            evaluate = HudAnimationGate.shouldEvaluate(hudDecision, evaluate);

            if (hudDecision == HudAnimationGate.Decision.REUSE_WORLD_POSE && HudPoseCache.applyTo(this)) {
                resetHeadTracking(false);
                applyHudHeadTracking(event, HudPoseCache.headBaseX(), HudPoseCache.headBaseY());
                HudAnimationGate.captureSample(this, HudPoseCache.headBaseX(), HudPoseCache.headBaseY());
                this.wasEvaluatedLastFrame = false;
            } else {
                boolean restoredHudSample = hudDecision == HudAnimationGate.Decision.HOLD_SAMPLED_POSE
                        && HudAnimationGate.restoreSample(this);
                resetHeadTracking(this.wasEvaluatedLastFrame);
                if (restoredHudSample) {
                    applyHudHeadTracking(event, HudAnimationGate.sampleHeadBaseX(), HudAnimationGate.sampleHeadBaseY());
                    this.wasEvaluatedLastFrame = false;
                } else {
                    AnimationFrameProfiler.Scope profilerScope = null;
                    if (evaluate) {
                        if (firstStep) {
                            this.hasUpdatedThisTick = true;
                        }
                        int renderFrameId = AnimationFrameProfiler.getRenderFrameId();
                        int boneCount = this.animationProcessor.getBoneCount();
                        int controllerCount = this.controllerSlots.size();
                        boolean previewMode = ModelPreviewRenderer.isPreview();
                        boolean extraPlayerMode = RenderContext.isGuiPreview();
                        boolean canReuse = (!previewMode || extraPlayerMode)
                                && this.lastAnimationEvaluationFrameId == renderFrameId
                                && Float.compare(this.lastAnimationEvaluationSeekTime, this.seekTime) == 0
                                && this.lastAnimationEvaluationActive == active
                                && this.lastAnimationEvaluationModel == this.currentModel
                                && this.lastAnimationEvaluationBoneCount == boneCount
                                && this.lastAnimationEvaluationControllerCount == controllerCount
                                && this.lastAnimationEvaluationPreviewMode == previewMode
                                && this.lastAnimationEvaluationExtraPlayerMode == extraPlayerMode
                                && Float.compare(this.lastAnimationEvaluationHeadPitch, event.getModelData().headPitch) == 0
                                && Float.compare(this.lastAnimationEvaluationNetHeadYaw, event.getModelData().netHeadYaw) == 0;
                        if (canReuse) {
                            AnimationFrameProfiler.logReusedEvaluation(this, event, this.seekTime);
                        } else {
                            if (this.lastAnimationEvaluationFrameId == renderFrameId) {
                                AnimationFrameProfiler.logReuseMiss(this, event,
                                        getReuseMissReason(active, boneCount, controllerCount),
                                        this.seekTime, this.lastAnimationEvaluationSeekTime, active,
                                        this.lastAnimationEvaluationActive, boneCount, this.lastAnimationEvaluationBoneCount,
                                        controllerCount, this.lastAnimationEvaluationControllerCount);
                            }
                            profilerScope = AnimationFrameProfiler.beginEvaluation(this, event, currentTick, this.seekTime, active, firstStep, boneCount, controllerCount);
                            try {
                                setupAnim(this.seekTime, firstStep);
                                this.animationProcessor.tickAnimation(event, active, shouldRenderOverlay());
                                afterSetupAnim(this.seekTime, firstStep);
                                this.wasAnimationActiveLastTick = active;
                                this.lastAnimationEvaluationFrameId = renderFrameId;
                                this.lastAnimationEvaluationSeekTime = this.seekTime;
                                this.lastAnimationEvaluationActive = active;
                                this.lastAnimationEvaluationModel = this.currentModel;
                                this.lastAnimationEvaluationBoneCount = boneCount;
                                this.lastAnimationEvaluationControllerCount = controllerCount;
                                this.lastAnimationEvaluationPreviewMode = previewMode;
                                this.lastAnimationEvaluationExtraPlayerMode = extraPlayerMode;
                                this.lastAnimationEvaluationHeadPitch = event.getModelData().headPitch;
                                this.lastAnimationEvaluationNetHeadYaw = event.getModelData().netHeadYaw;
                            } finally {
                                AnimationFrameProfiler.endEvaluation(profilerScope);
                            }
                        }
                    }
                    applyHeadTracking(event, evaluate);
                    HudAnimationGate.captureSample(this);
                    this.wasEvaluatedLastFrame = evaluate;
                }
            }
        }
    }

    private static float sanitizePartialTick(float partialTick) {
        return Float.isFinite(partialTick) ? Mth.clamp(partialTick, 0.0f, 1.0f) : 0.0f;
    }

    private String getReuseMissReason(boolean active, int boneCount, int controllerCount) {
        if (this.lastAnimationEvaluationModel != this.currentModel) return "model_changed";
        if (this.lastAnimationEvaluationBoneCount != boneCount || this.lastAnimationEvaluationControllerCount != controllerCount) return "shape_changed";
        if (Float.compare(this.lastAnimationEvaluationSeekTime, this.seekTime) != 0) return "seekTime_changed";
        if (this.lastAnimationEvaluationActive != active) return "active_changed";
        return "unknown";
    }

    public void applyHeadTracking(HostActionEvent<? extends HostAnimatable<TEntity>> event, boolean evaluated) {
    }

    public void applyHudHeadTracking(HostActionEvent<? extends HostAnimatable<TEntity>> event, float baseRotationX, float baseRotationY) {
    }

    public void resetHeadTracking(boolean evaluated) {
    }

    public HostAnimationProcessor<TEntity> getEvaluationContext() {
        return this.animationProcessor;
    }

    public void initAnimationControllers(@NotNull BuiltModel model, Map<String, List<HostMolangScript>> eventScripts) {
        reset();
        this.currentModel = new ModelScenePose(model);
        this.animationMap = eventScripts == null ? Map.of() : eventScripts;
        registerAnimationControllers();
        this.animationProcessor.initModel(this.currentModel, resolveAnimationLibrary(), resolveExtraFunctions(), this.animationMap);
        setCurrentModel(this.currentModel);
    }

    public void clearAnimationControllers() {
        if (this.currentModel != null) {
            BuiltModel model = this.currentModel.getGeoModel();
            Map<String, List<HostMolangScript>> scripts = this.animationMap;
            reset();
            initAnimationControllers(model, scripts);
        }
    }

    public @Nullable final ModelScenePose getCurrentModel() {
        return this.currentModel;
    }

    public void setCurrentModel(@Nullable ModelScenePose model) {
    }

    public boolean shouldSkipAnimation(HostActionEvent<?> event) {
        return true;
    }

    public void resetAnimationState() {
        this.needsReset = true;
    }

    public void executeExpression(HostMolangScript value, boolean isClientPlayer, boolean executeBeforeAnimation, @Nullable Consumer<String> consumer) {
        this.animationProcessor.execute(value, isClientPlayer, executeBeforeAnimation, consumer);
    }

    public Map<String, dev.spm.core.scene.MolangValue> getPropertyGetter() {
        return this.animationProcessor.typedVariables();
    }

    public void markModelInitialized() {
        this.modelInitialized = true;
    }

    public boolean isModelInitialized() {
        return this.modelInitialized;
    }

    public @Nullable ILogger getLogger() {
        return null;
    }

    public boolean isDebugMode() {
        return Minecraft.getInstance().level == this.entity.level() && !this.entity.isRemoved();
    }

    public void setAnimationState(String name, HostPlayState.State state) {
        this.animationStates.put(name, state);
    }

    public HostPlayState.State getAnimationState(String name) {
        return this.animationStates.getOrDefault(name, HostPlayState.State.IDLE);
    }
}
