package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.audio.*;
import com.micaftic.morpher.client.animation.debug.AnimationFrameProfiler;
import com.micaftic.morpher.client.animation.molang.MolangEventDispatcher;
import com.micaftic.morpher.client.animation.molang.MolangWatchRegistry;
import com.micaftic.morpher.client.animation.molang.PhysicsManager;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.core.HostMolangScript;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.renderer.AnimationDebugOverlay;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.util.*;
import com.micaftic.morpher.util.log.ChatLogger;
import com.micaftic.morpher.util.log.ILogger;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;

public abstract class SpmEntity<T extends Entity> extends HostAnimatable<T> {
    private String modelId;

    protected ModelAssembly modelAssembly;

    protected ModelWrapper renderShape;

    protected boolean loaded;

    private int updateTicks;

    @Nullable
    private PhysicsManager bones;

    @Nullable
    private PhysicsManager previewBones;

    @Nullable
    private PhysicsManager extraPlayerBones;

    protected final PhysicsManager physicsManager = new PhysicsManager();

    @Nullable
    private MolangWatchRegistry boneLookup;

    @Nullable
    private List<HostMolangScript> renderLayers;

    @Nullable
    private Future<HostActionEvent<?>> modelFuture;

    private int asyncSubmitFrameId = -1;

    @Nullable
    public abstract SpmEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault);

    public abstract BuiltModel getAnimationProcessor();

    public SpmEntity(T t, boolean registerWithCache) {
        super(t);
        this.modelId = "default";
        if (registerWithCache) {
            EntityRenderCache.register(this);
        }
    }

    public PhysicsManager getPhysicsManager() {
        com.micaftic.morpher.client.render.RenderContext.PhysicsDomain physicsDomain =
                com.micaftic.morpher.client.render.RenderContext.physicsDomain();
        if (physicsDomain == com.micaftic.morpher.client.render.RenderContext.PhysicsDomain.PREVIEW) {
            if (this.previewBones == null) {
                this.previewBones = new PhysicsManager();
            }
            return this.previewBones;
        }
        if (physicsDomain == com.micaftic.morpher.client.render.RenderContext.PhysicsDomain.EXTRA_PLAYER) {
            if (this.extraPlayerBones == null) {
                this.extraPlayerBones = new PhysicsManager();
            }
            return this.extraPlayerBones;
        }
        if (physicsDomain == com.micaftic.morpher.client.render.RenderContext.PhysicsDomain.FIRST_PERSON) {
            return this.physicsManager;
        }
        if (this.bones == null) {
            this.bones = new PhysicsManager();
        }
        return this.bones;
    }

    @Nullable
    public List<HostMolangScript> getRenderLayers() {
        return this.renderLayers;
    }

    public void setBoneLookup(@Nullable MolangWatchRegistry watchRegistry) {
        this.boneLookup = watchRegistry;
        getEvaluationContext().setWatchRegistry(watchRegistry);
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        super.setupAnim(seekTime, isFirstPerson);
        getEvaluationContext().setWatchRegistry(this.boneLookup);
    }

    public void tickModel() {
        if (this.updateTicks < this.entity.tickCount) {
            refreshModel();
            this.updateTicks = this.entity.tickCount;
        }
    }

    public final ModelAssembly getModelAssembly() {
        return this.modelAssembly;
    }

    @Override
    public @org.jetbrains.annotations.Nullable com.micaftic.morpher.resource.models.ModelProperties resolveModelProperties() {
        return this.modelAssembly != null && this.modelAssembly.getModelData() != null
                ? this.modelAssembly.getModelData().getModelProperties()
                : null;
    }

    public final boolean referencesModelAssembly(ModelAssembly assembly) {
        return assembly != null && (this.modelAssembly == assembly
                || (this.renderShape != null && this.renderShape.context == assembly));
    }

    public final void setModelId(String str) {
        this.modelId = str;
        refreshModel();
    }

    protected void refreshModel() {
        ClientModelManager.getModelContext(this.modelId).ifPresentOrElse(assembly -> {
            updateRenderShape(assembly, false);
        }, () -> {
            if (ClientModelManager.isModelLoadPending(this.modelId) && hasRenderableModel()) {
                return;
            }
            ModelAssembly modelAssembly = ClientModelManager.getLocalModelContext();
            if (modelAssembly == null || !modelAssembly.isRuntimeResident()) {
                if (this.renderShape != null || this.modelAssembly != null) {
                    clearModel();
                }
                return;
            }
            updateRenderShape(modelAssembly, true);
        });
        if (this.renderShape != null) {
            if ((this.renderShape.context != this.modelAssembly || this.renderShape.isDefault != this.loaded) && this.renderShape.isValid()) {
                this.modelAssembly = this.renderShape.context;
                this.loaded = this.renderShape.isDefault;
                onModelLoaded(this.modelAssembly);
                if (this.modelAssembly.isGltf()) {
                    return;
                }
                initAnimationControllers(getAnimationProcessor(), this.modelAssembly.getExpressionCache().getEvents());
                return;
            }
            return;
        }
        if (this.modelAssembly != null) {
            clearModel();
        }
    }

    private void updateRenderShape(ModelAssembly assembly, boolean isDefault) {
        synchronized (assembly) {
            if (!assembly.isRuntimeResident()) {
                return;
            }
            if (this.renderShape == null
                    || this.renderShape.isDefault != isDefault
                    || assembly != this.renderShape.context) {
                this.renderShape = buildRenderShape(assembly, isDefault);
            }
        }
    }

    public final ModelWrapper getRenderShape() {
        return this.renderShape;
    }

    public void onModelLoaded(ModelAssembly modelAssembly) {
        this.renderShape.audioProvider = AudioStreamCache.getOrCreateProvider(modelAssembly);
        this.renderLayers = modelAssembly.getExpressionCache().getEvents().get(MolangEventDispatcher.DEFER);
    }

    public void clearModel() {
        this.modelAssembly = null;
        this.renderLayers = null;
        this.renderShape = null;
        this.loaded = false;
        reset();
    }

    @Override
    public void reset() {
        if (this.modelFuture != null) {
            awaitAsyncResult();
        }
        super.reset();
        this.bones = null;
        this.previewBones = null;
        this.extraPlayerBones = null;
        this.modelFuture = null;
        this.asyncSubmitFrameId = -1;
        this.updateTicks = 0;
    }

    public void resetModel() {
        this.modelId = "default";
        this.modelInitialized = false;
        clearModel();
    }

    public final String getModelId() {
        return this.modelId;
    }

    public boolean isModelReady() {
        return this.renderShape != null
                && !this.renderShape.isDefault
                && this.renderShape.context.isRuntimeResident()
                && this.renderShape.isValid();
    }

    public boolean hasRenderableModel() {
        return this.modelAssembly != null
                && this.renderShape != null
                && this.renderShape.context.isRuntimeResident()
                && this.renderShape.isValid();
    }

    @Override
    public boolean shouldSkipAnimation(HostActionEvent<?> event) {
        return event.isFirstPerson() || OculusCompat.isPBRActive();
    }

    @Nullable
    public final HostMolangScript resolveExpression(String str) {
        return getModelAssembly().getExpressionCache().getFunctions().get(str);
    }

    @Override
    protected Map<String, HostMolangScript> resolveExtraFunctions() {
        ModelAssembly assembly = getModelAssembly();
        if (assembly == null || assembly.getExpressionCache() == null) {
            return Map.of();
        }
        return assembly.getExpressionCache().getFunctions();
    }

    @Override
    public Optional<IAudioStreamFactory> getAudioStreamFactory(String str) {
        AudioTrackData trackData;
        if (this.renderShape.audioProvider != null && (trackData = getModelAssembly().getExpressionCache().getSoundEffects().get(str)) != null && trackData.getData() != null && trackData.getCodec() != AudioCodec.UNDEFINED) {
            IAudioStreamProvider streamProvider = this.renderShape.audioProvider;
            return Optional.of(() -> {
                return streamProvider.createAudioStream(trackData);
            });
        }
        return Optional.empty();
    }

    @Override
    public ILogger getLogger() {
        if (AnimationDebugOverlay.isDebugActive()) {
            return ChatLogger.INSTANCE;
        }
        return null;
    }

    public void submitAsyncUpdate(float partialTick) {
        // Capture the animation time base on the render thread. The worker must not read
        // entity.tickCount at execution time: a delayed/culled task would compute a time
        // that is ahead of its submission frame, making seekTime advance in bursts followed
        // by freezes (visible as ~20Hz stutter on other players' models).
        int capturedTickCount = this.entity.tickCount;
        int renderFrameId = AnimationFrameProfiler.getRenderFrameId();
        java.lang.invoke.VarHandle.releaseFence();
        this.asyncSubmitFrameId = renderFrameId;
        this.modelFuture = YSMThreadPool.submitCallable(() -> {
            try {
                HostActionEvent<?> event = super.processAnimationImpl(partialTick, capturedTickCount, false);
                java.lang.invoke.VarHandle.releaseFence();
                return event;
            } catch (Throwable th) {
                java.lang.invoke.VarHandle.releaseFence();
                throw th;
            }
        });
    }

    public boolean hasPendingAsyncUpdate() {
        return this.modelFuture != null;
    }

    @Override
    @Nullable
    public HostActionEvent<?> processAnimationImpl(float partialTick, boolean isFirstPerson) {
        RenderSystem.assertOnRenderThread();
        if (!isModelReady()) {
            if (this.modelFuture != null) {
                awaitAsyncResult();
            }
            return null;
        }
        boolean isGuiPreview = com.micaftic.morpher.client.render.RenderContext.isAnyPreview();
        boolean useAsyncResult = !isGuiPreview && !(this.entity instanceof LocalPlayer && InputStateKey.hasLocalInteractionState());
        if (useAsyncResult) {
            int renderFrameId = AnimationFrameProfiler.getRenderFrameId();
            if (this.modelFuture != null && this.asyncSubmitFrameId != renderFrameId) {
                // The pending task belongs to a frame in which this entity was not rendered;
                // its time base is stale. Discard it and submit a fresh task below.
                awaitAsyncResult();
            }
            if (this.modelFuture == null && this.asyncSubmitFrameId != renderFrameId) {
                // Submit only while the entity is actually being rendered this frame. Keeping the
                // worker queue bounded by the visible set (and submit order equal to render order)
                // keeps frame pacing regular instead of stalling on accumulated background tasks.
                submitAsyncUpdate(partialTick);
            }
            if (this.modelFuture != null) {
                HostActionEvent<?> event = awaitAsyncResult();
                if (event != null) {
                    return event;
                }
            }
        } else if (this.modelFuture != null) {
            awaitAsyncResult();
        }
        return super.processAnimationImpl(partialTick, isFirstPerson);
    }

    public HostActionEvent<?> awaitAsyncResult() {
        if (this.modelFuture != null) {
            HostActionEvent<?> event = null;
            try {
                event = this.modelFuture.get();
                java.lang.invoke.VarHandle.acquireFence();
            } catch (InterruptedException e) {
            } catch (Throwable th) {
                th.printStackTrace();
            }
            this.modelFuture = null;
            return event;
        }
        return null;
    }

    public boolean supportsAsync() {
        return true;
    }

    public static class ModelWrapper {

        public final ModelAssembly context;

        public final boolean isDefault;

        @Nullable
        public IAudioStreamProvider audioProvider;

        public ModelWrapper(ModelAssembly modelAssembly, boolean isDefault) {
            this.context = modelAssembly;
            this.isDefault = isDefault;
        }

        public boolean isValid() {
            return true;
        }
    }
}
