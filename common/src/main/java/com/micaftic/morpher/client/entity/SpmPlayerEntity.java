package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.animation.condition.ArmorConditions;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.render.BuiltModel;
import dev.spm.core.scene.PoseAnimationLibrary;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class SpmPlayerEntity extends SpmEntity<LocalPlayer> {

    private final PlayerCapability playerCapability;

    public SpmPlayerEntity(LocalPlayer player, PlayerCapability capability) {
        super(player, false);
        this.playerCapability = capability;
        setModelId(capability.getModelId());
    }

    @Override
    public void registerAnimationControllers() {
        getModelAssembly().getAnimationBundle().getArmControllerInstaller().accept(this);
    }

    public PlayerCapability getPlayerCapability() {
        return this.playerCapability;
    }

    @Override
    public boolean shouldSkipAnimation(HostActionEvent<?> event) {
        return false;
    }

    @Override
    public void tickModel() {
        if (this.playerCapability.getModelAssembly() != getModelAssembly()) {
            setModelId(this.playerCapability.getModelId());
        }
    }

    @Override
    @Nullable
    public SpmEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault) {
        return this.playerCapability.getRenderShape();
    }

    @Nullable
    public ControllerSpec getAnimationEntries(String str) {
        return getModelAssembly().getAnimationBundle().getAnimationEntries().get(str);
    }

    @Override
    public @Nullable ControllerSpec getControllerSpec(String name) {
        return getAnimationEntries(name);
    }

    @Override
    protected @Nullable PoseAnimationLibrary resolveAnimationLibrary() {
        ModelAssembly assembly = getModelAssembly();
        if (assembly == null || assembly.getAnimationBundle() == null) {
            return null;
        }
        return assembly.getAnimationBundle().getArmAnimationLibrary();
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return this.playerCapability.getTextureLocation();
    }

    @Override
    public float getHeightScale() {
        return getModelAssembly().getModelData().getModelProperties().getHeightScale();
    }

    @Override
    public float getWidthScale() {
        return getModelAssembly().getModelData().getModelProperties().getWidthScale();
    }

    @Override
    @Nullable
    public ClipSpec getAnimation(String str) {
        return getModelAssembly().getAnimationBundle().getArmAnimations().get(str);
    }

    public ArmorConditions getArmModelProcessor() {
        return getModelAssembly().getAnimationBundle().getModelProcessor();
    }

    @Override
    public BuiltModel getAnimationProcessor() {
        return getModelAssembly().getAnimationBundle().getArmModel();
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        getEvaluationContext().setRoamingProperties(this.playerCapability.getServerVarContainer());
    }
}
