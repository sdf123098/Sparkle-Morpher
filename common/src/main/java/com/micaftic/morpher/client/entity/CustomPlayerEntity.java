package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.geckolib3.core.controller.controllers.UnifiedPlayerActionController;
import com.micaftic.morpher.client.animation.molang.MolangEventDispatcher;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.core.compat.oculus.OculusCompat;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.core.enums.AnimationState;
import com.micaftic.morpher.resource.models.ModelProperties;
import com.micaftic.morpher.molang.runtime.Struct;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SPlayAnimationPacket;
import com.micaftic.morpher.util.data.OrderedStringMap;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public abstract class CustomPlayerEntity extends LivingAnimatable<Player> implements RoamingPropertyHolder {

    public final boolean isLocalPlayer;

    public boolean isModelSwitching;

    public String selectedModelId;

    public boolean isDisabled;

    private List<IValue> syncIValues;

    public CustomPlayerEntity(Player player, boolean isLocalPlayer, boolean isActive) {
        super(player, isActive);
        this.isModelSwitching = false;
        this.selectedModelId = "idle";
        this.isDisabled = false;
        this.syncIValues = null;
        this.isLocalPlayer = isLocalPlayer;
        if (player instanceof LocalPlayer) {
            markModelInitialized();
        }
    }

    @Override
    public void registerAnimationControllers() {
        getModelAssembly().getAnimationBundle().getPlayerControllerInstaller().accept(this);
    }

    @Override
    public void resetModel() {
        super.resetModel();
        this.syncIValues = null;
    }

    @Override
    public void reset() {
        super.reset();
        this.isModelSwitching = false;
        this.selectedModelId = "idle";
        this.isDisabled = false;
    }

    @Override
    public boolean shouldSkipAnimation(AnimationEvent<?> event) {
        return event.isFirstPerson() || (!this.isLocalPlayer && OculusCompat.isPBRActive());
    }

    @Override
    @Nullable
    public Struct getServerVarContainer() {
        return null;
    }

    public boolean isLocalPlayerModel() {
        return this.isLocalPlayer;
    }

    @Override
    public void onModelLoaded(ModelAssembly context) {
        super.onModelLoaded(context);
        this.syncIValues = context.getExpressionCache().getEvents().get(MolangEventDispatcher.SYNC);
    }

    public void requestModelSwitch(String str) {
        String animationName = resolvePlayableAnimation(str);
        if (animationName != null) {
            this.selectedModelId = animationName;
            this.isModelSwitching = true;
            this.isDisabled = true;
            return;
        }
        this.isModelSwitching = false;
    }

    private @Nullable String resolvePlayableAnimation(String animationName) {
        if (animationName == null || animationName.isBlank()) {
            return null;
        }
        if (getAnimation(animationName) != null) {
            return animationName;
        }
        ModelProperties properties = getModelAssembly().getModelData().getModelProperties();
        String resolved = resolveExtraAnimationValue(properties.getExtraAnimation(), animationName);
        if (resolved != null) {
            return resolved;
        }
        for (OrderedStringMap<String, String> group : properties.getExtraAnimationClassify().values()) {
            resolved = resolveExtraAnimationValue(group, animationName);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private @Nullable String resolveExtraAnimationValue(OrderedStringMap<String, String> animations, String key) {
        if (animations == null || key == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : animations.entrySet()) {
            if (key.equals(entry.getKey())) {
                String value = entry.getValue();
                return value != null && getAnimation(value) != null ? value : null;
            }
        }
        return null;
    }

    public void enableModel() {
        this.isDisabled = false;
    }

    public boolean isModelSwitching() {
        return this.isModelSwitching;
    }

    public boolean isDisabledState() {
        return this.isDisabled;
    }

    public String getSelectedModelId() {
        return this.selectedModelId;
    }

    public void clearModelSwitch() {
        this.isModelSwitching = false;
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        super.setupAnim(seekTime, isFirstPerson);
        getEvaluationContext().setRoamingProperties(getServerVarContainer());
    }

    @Override
    public void afterSetupAnim(float seekTime, boolean isFirstPerson) {
        super.afterSetupAnim(seekTime, isFirstPerson);
        if (this.isLocalPlayer && isFirstPerson && isModelSwitching() && getAnimationState(getCapControllerKey()) == AnimationState.IDLE) {
            clearModelSwitch();
            if (NetworkHandler.isClientConnected()) {
                NetworkHandler.sendToServer(C2SPlayAnimationPacket.createDefault());
            }
        }
    }

    private String getCapControllerKey() {
        return UnifiedPlayerActionController.CAP_CONTROLLER_KEY;
    }

    public void executeAnimationExpression(FloatArrayList floatArrayList) {
        if (this.syncIValues != null) {
            executeExpression(MolangEventDispatcher.createExpression(this.syncIValues, floatArrayList), true, false, null);
        }
    }
}
