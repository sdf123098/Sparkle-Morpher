package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangScript;
import com.micaftic.morpher.client.entity.SpmEntity;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.entity.RoamingPropertyHolder;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.render.BoneIds;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class MaidCapability extends LivingAnimatable<LivingEntity> implements RoamingPropertyHolder {
    private static final Map<UUID, MaidCapability> CACHE = new ConcurrentHashMap<>();

    @Nullable
    private RoamingStruct serverVars;
    private String rouletteAnimation = "";
    private boolean cloudModelStateApplied;
    private boolean localModelStateApplied;
    private boolean peerModelStateApplied;
    private Map<String, Float> displayMotionVariables;

    private MaidCapability(LivingEntity entity) {
        super(entity, true);
    }

    public static Optional<MaidCapability> get(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !TouhouMaidCompat.isMaidEntity(entity)) {
            return Optional.empty();
        }
        return Optional.of(CACHE.compute(living.getUUID(), (uuid, current) ->
                current == null || current.getEntity() != living ? new MaidCapability(living) : current));
    }

    /** Cloud bindings have priority over the optional legacy maid synchronization. */
    public boolean hasLocalState() {
        return this.localModelStateApplied;
    }

    public void applyLocalState(String modelId, String textureId) {
        awaitAsyncResult();
        this.cloudModelStateApplied = false;
        this.localModelStateApplied = true;
        this.peerModelStateApplied = false;
        clearDisplayMotion();
        this.rouletteAnimation = "";
        this.serverVars = null;
        initModelWithTexture(modelId, textureId);
        setForceDisabled(false);
    }

    public boolean hasPeerState() {
        return this.peerModelStateApplied;
    }

    public boolean hasCloudState() {
        return this.cloudModelStateApplied;
    }

    public void applyPeerState(String modelId, String textureId) {
        if (this.localModelStateApplied) return;
        awaitAsyncResult();
        this.peerModelStateApplied = true;
        this.cloudModelStateApplied = false;
        clearDisplayMotion();
        this.rouletteAnimation = "";
        this.serverVars = null;
        initModelWithTexture(modelId, textureId);
        setForceDisabled(false);
    }

    public void clearPeerState() {
        if (!this.peerModelStateApplied) return;
        this.peerModelStateApplied = false;
        clearDisplayMotion();
        this.rouletteAnimation = "";
        this.serverVars = null;
        resetModel();
        setForceDisabled(false);
    }

    public void clearLocalState() {
        if (!this.localModelStateApplied) return;
        this.localModelStateApplied = false;
        clearDisplayMotion();
        resetModel();
        setForceDisabled(false);
    }

    public void applyCloudState(String modelId, String textureId) {
        if (this.localModelStateApplied
                || (this.peerModelStateApplied && !com.micaftic.morpher.core.network.NetworkAccess.state().preset().prefersCloud())) {
            return;
        }
        this.peerModelStateApplied = false;
        if (modelId == null || modelId.isBlank()) {
            clearCloudState();
            return;
        }
        this.cloudModelStateApplied = true;
        this.rouletteAnimation = "";
        this.serverVars = null;
        setForceDisabled(false);
        initModelWithTexture(modelId, textureId == null ? "default" : textureId);
    }

    /** Removing a Cloud binding restores the original maid renderer. */
    public void clearCloudState() {
        if (!this.cloudModelStateApplied) {
            return;
        }
        this.cloudModelStateApplied = false;
        this.rouletteAnimation = "";
        this.serverVars = null;
        resetModel();
        setForceDisabled(false);
    }

    public String getRouletteAnimation() {
        return this.rouletteAnimation;
    }

    /** Client display values only. Does not change the maid's native AI or equipment. */
    public void applyDisplayMotion(String animation, Map<String, Float> variables) {
        if (this.serverVars != null && java.util.Objects.equals(this.displayMotionVariables, variables)
                && this.rouletteAnimation.equals(animation)) {
            return;
        }
        awaitAsyncResult();
        var values = new Int2FloatOpenHashMap();
        variables.forEach((name, value) -> values.put(BoneIds.id(name), value.floatValue()));
        this.serverVars = RoamingStruct.readOnly(values);
        this.displayMotionVariables = Map.copyOf(variables);
        this.rouletteAnimation = animation;
    }

    public void clearDisplayMotion() {
        if (this.displayMotionVariables == null) return;
        awaitAsyncResult();
        this.displayMotionVariables = null;
        this.serverVars = null;
        this.rouletteAnimation = "";
    }

    public void executeMolang(String expression) {
        if (!isModelReady() || expression == null || expression.isBlank()) {
            return;
        }
        executeExpression(HostMolangScript.compile(expression), true, false, null);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void registerAnimationControllers() {
        Object installer = getModelAssembly().getAnimationBundle().getMaidControllerInstaller();
        if (installer instanceof Consumer consumer) {
            consumer.accept(this);
        }
    }

    @Override
    @NotNull
    public SpmEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault) {
        return new TexturedModelWrapper(modelAssembly, isDefault, true, true, 600);
    }

    @Override
    @Nullable
    public RoamingStruct getServerVarContainer() {
        return this.serverVars;
    }

    @Override
    public boolean shouldSkipAnimation(HostActionEvent<?> event) {
        return event.isFirstPerson();
    }
}
