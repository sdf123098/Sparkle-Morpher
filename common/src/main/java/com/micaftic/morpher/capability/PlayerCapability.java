package com.micaftic.morpher.capability;

import com.micaftic.morpher.cloud.client.CloudMotionSources;
import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.client.animation.molang.struct.RoamingSyncBatch;
import com.micaftic.morpher.capability.client.PlayerCapabilityClientStore;
import com.micaftic.morpher.core.compat.bettercombat.BetterCombatCompat;
import com.micaftic.morpher.core.compat.firstperson.FirstPersonCompat;
import com.micaftic.morpher.util.CameraUtil;
import com.micaftic.morpher.util.LocalModelSettingsStore;
import com.micaftic.morpher.client.entity.PlayerEntityFrameState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.core.processor.IBone;
import com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct;
import com.micaftic.morpher.molang.runtime.Struct;
import com.micaftic.morpher.network.ClientNetworkBridge;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatMaps;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
public final class PlayerCapability extends CustomPlayerEntity {

    public static Optional<PlayerCapability> get(Player player) {
        return PlayerCapabilityClientStore.get(player);
    }

    public static Optional<PlayerCapability> get(Entity entity) {
        if (!(entity instanceof Player player)) {
            return Optional.empty();
        }
        return get(player);
    }

    private final Int2ReferenceOpenHashMap<MolangVarHolder> molangVarsMap;

    private int currentModelHashId;

    private Struct serverVarContainer;

    private boolean pendingLocalSettingsRestore;

    private boolean hasRenderState;

    private float renderStateWalkAnimationSpeed;

    private float renderStateWalkAnimationPos;

    private float renderStateBodyRot;

    private float renderStateNetHeadYaw;

    private float renderStateHeadPitch;

    public PlayerCapability(Player player) {
        super(player, ClientNetworkBridge.isLocalPlayer(player), true);
        this.molangVarsMap = new Int2ReferenceOpenHashMap<>(8);
    }

    @Override
    public PlayerEntityFrameState createPositionTracker(Player player) {
        return new PlayerEntityFrameState(player, ClientNetworkBridge.isLocalPlayer(player));
    }

    @Override
    public PlayerEntityFrameState getPositionTracker() {
        return (PlayerEntityFrameState) super.getPositionTracker();
    }

    @Nullable
    public Struct getServerVarContainer() {
        return this.serverVarContainer;
    }

    private RoamingStruct createLocalRoamingStruct(int modelHashId, Int2FloatOpenHashMap variables) {
        RoamingStruct roamingStruct = new RoamingStruct(modelHashId, variables);
        this.pendingLocalSettingsRestore = true;
        return roamingStruct;
    }

    public void beginRenderState(AvatarRenderState renderState) {
        this.hasRenderState = true;
        this.renderStateWalkAnimationSpeed = renderState.walkAnimationSpeed;
        this.renderStateWalkAnimationPos = renderState.walkAnimationPos;
        this.renderStateBodyRot = renderState.bodyRot;
        this.renderStateNetHeadYaw = renderState.yRot;
        this.renderStateHeadPitch = renderState.xRot;
    }

    public void endRenderState() {
        this.hasRenderState = false;
    }

    public boolean hasRenderState() {
        return this.hasRenderState;
    }

    public float getRenderStateWalkAnimationSpeed() {
        return this.renderStateWalkAnimationSpeed;
    }

    public float getRenderStateWalkAnimationPos() {
        return this.renderStateWalkAnimationPos;
    }

    public float getRenderStateBodyRot() {
        return this.renderStateBodyRot;
    }

    public float getRenderStateNetHeadYaw() {
        return this.renderStateNetHeadYaw;
    }

    public float getRenderStateHeadPitch() {
        return this.renderStateHeadPitch;
    }

    @Override
    public void onModelLoaded(ModelAssembly context) {
        super.onModelLoaded(context);
        this.currentModelHashId = getModelAssembly().getModelData().getHashId();
    }

    @Override
    public void clearModel() {
        this.currentModelHashId = 0;
        super.clearModel();
    }

    @Override
    public void setCurrentModel(AnimatedGeoModel model) {
        super.setCurrentModel(model);
        MolangVarHolder varHolder = this.molangVarsMap.get(this.currentModelHashId);
        if (isLocalPlayerModel()) {
            // Local model settings must not depend on a server variable table.
            this.serverVarContainer = createLocalRoamingStruct(
                    this.currentModelHashId,
                    varHolder == null ? null : varHolder.currentVars
            );
            return;
        }
        if (varHolder != null && varHolder.currentVars != null) {
            this.serverVarContainer = new Int2FloatOpenHashMapStruct(varHolder.currentVars);
            return;
        }
        this.serverVarContainer = null;
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        super.setupAnim(seekTime, isFirstPerson);
        if (this.pendingLocalSettingsRestore && this.serverVarContainer instanceof RoamingStruct roamingStruct) {
            this.pendingLocalSettingsRestore = false;
            LocalModelSettingsStore.restore(getModelId(), roamingStruct);
        }
    }

    @Override
    public void reset() {
        this.serverVarContainer = null;
        this.pendingLocalSettingsRestore = false;
        super.reset();
    }

    @Override
    public void applyHeadTracking(AnimationEvent<? extends AnimatableEntity<Player>> event, boolean wasAnimEvaluated) {
        super.applyHeadTracking(event, wasAnimEvaluated);
        AnimatedGeoModel model2 = getCurrentModel();
        if (model2 != null && isLocalPlayerModel()) {
            // A full-body first-person mod can render the local player from either animation pass.
            // Always derive head visibility from the actual camera state so the camera never ends
            // up inside the model's head.
            boolean shouldHideHead = CameraUtil.isFirstPerson(this);
            if (model2.allHeadBone() != null) {
                model2.allHeadBone().setHidden(shouldHideHead);
            }
            if (FirstPersonCompat.isLoaded()) {
                if (model2.viewLocatorBone() != null) {
                    FirstPersonCompat.setCameraDistance(model2.viewLocatorBone().getPivotY() * getWidthScale());
                } else if (wasAnimEvaluated && !model2.headBones().isEmpty()) {
                    IBone bone = model2.headBones().get(model2.headBones().size() - 1);
                    FirstPersonCompat.setCameraDistance(bone == null ? 24.0f : bone.getPivotY() * getWidthScale());
                }
            }
        }
    }

    @Override
    public void resetHeadTracking(boolean wasAnimEvaluated) {
        super.resetHeadTracking(wasAnimEvaluated);
        AnimatedGeoModel model2 = getCurrentModel();
        if (model2 != null && isLocalPlayerModel()) {
            if (model2.allHeadBone() != null) {
                model2.allHeadBone().setHidden(false);
            }
        }
    }

    public void updateMolangVars(int i, Int2FloatOpenHashMap int2FloatOpenHashMap) {
        MolangVarHolder varHolder = this.molangVarsMap.computeIfAbsent(i, i2 -> {
            return new MolangVarHolder();
        });
        if (isLocalPlayerModel()) {
            if (varHolder.currentVars == null) {
                varHolder.currentVars = int2FloatOpenHashMap;
                varHolder.applyPendingDeltas();
                if (i == this.currentModelHashId) {
                    this.serverVarContainer = createLocalRoamingStruct(i, int2FloatOpenHashMap);
                    clearAnimationControllers();
                    return;
                }
                return;
            }
            return;
        }
        varHolder.currentVars = int2FloatOpenHashMap;
        varHolder.applyPendingDeltas();
        if (i == this.currentModelHashId) {
            this.serverVarContainer = new Int2FloatOpenHashMapStruct(int2FloatOpenHashMap);
        }
    }

    public boolean hasMolangVars(int i) {
        return this.molangVarsMap.containsKey(i);
    }

    private void applyMolangDelta(int i, Int2FloatMap int2FloatMap) {
        if (i == this.currentModelHashId && this.entity.getVehicle() != null && this.entity.getVehicle().getFirstPassenger() == this.entity) {
            VehicleCapability.get(this.entity.getVehicle()).ifPresent(cap -> {
                cap.updateFloatMap(int2FloatMap);
            });
        }
    }

    public void enqueueMolangDelta(int i, Int2FloatMap int2FloatMap) {
        if (!isLocalPlayerModel() && !int2FloatMap.isEmpty()) {
            MolangVarHolder varHolder = this.molangVarsMap.computeIfAbsent(i, i2 -> {
                return new MolangVarHolder();
            });
            if (varHolder.currentVars != null) {
                varHolder.currentVars.putAll(int2FloatMap);
            } else {
                varHolder.pendingDeltas.enqueue(int2FloatMap);
            }
            applyMolangDelta(i, int2FloatMap);
        }
    }

    /** Advance Cloud motion while the first-person camera hides the body. */
    public void advanceCloudMotion() {
        awaitAsyncResult();
        endRenderState();
        processAnimationImpl(0, false);
    }

    public void tickAnimations() {
        if (isLocalPlayerModel() && this.currentModelHashId != 0) {
            Struct struct = this.serverVarContainer;
            if (struct instanceof RoamingStruct roamingStruct) {
                java.util.Map<String, Float> current = new java.util.HashMap<>();
                roamingStruct.forEachVar(name -> {
                    Object value = roamingStruct.getProperty(StringPool.computeIfAbsent(name));
                    if (current.size() < RoamingStruct.MAX_VARS && !name.isBlank()
                            && name.length() <= RoamingStruct.MAX_VAR_NAME_LENGTH && value instanceof Number number
                            && Float.isFinite(number.floatValue())) current.put(name, number.floatValue());
                });
                CloudMotionSources.roaming(this, current);
                if (roamingStruct.hasPendingChanges()) {
                    RoamingSyncBatch syncBatch = roamingStruct.consumePendingBoneData();
                    LocalModelSettingsStore.save(getModelId(), syncBatch.changedVariables());
                    applyMolangDelta(syncBatch.modelHashId(), syncBatch.changedVariables());
                    java.util.Map<String, Float> changes = new java.util.HashMap<>();
                    for (Int2FloatMap.Entry entry : syncBatch.changedVariables().int2FloatEntrySet()) {
                        String name = StringPool.getString(entry.getIntKey());
                        if (!name.isBlank() && name.length() <= RoamingStruct.MAX_VAR_NAME_LENGTH
                                && Float.isFinite(entry.getFloatValue())) changes.put(name, entry.getFloatValue());
                    }
                    CloudMotionSources.roaming(this, changes);
                }
            }
        }
    }

    public void copyFrom(PlayerCapability playerCapability) {
        this.molangVarsMap.putAll(playerCapability.molangVarsMap);
        initModelWithTexture(playerCapability.getModelId(), playerCapability.currentTextureName);
        setForceDisabled(playerCapability.isForceDisabled());
        playerCapability.molangVarsMap.clear();
        playerCapability.serverVarContainer = null;
    }

    @Override
    @NotNull
    public LivingAnimatable<Player>.TexturedModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isActive) {
        return new TexturedModelWrapper(modelAssembly, isActive, true, true, 600);
    }

    private static class MolangVarHolder {

        public volatile Int2FloatOpenHashMap currentVars;

        public final ObjectArrayFIFOQueue<Int2FloatMap> pendingDeltas = new ObjectArrayFIFOQueue<>(4);

        private MolangVarHolder() {
        }

        public void applyPendingDeltas() {
            while (!this.pendingDeltas.isEmpty()) {
                this.currentVars.putAll(this.pendingDeltas.dequeue());
            }
        }
    }
}
