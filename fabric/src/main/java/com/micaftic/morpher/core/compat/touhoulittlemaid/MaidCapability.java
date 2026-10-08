package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.entity.GeoEntity;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.molang.parser.ParseException;
import com.micaftic.morpher.molang.runtime.Struct;
import com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct;
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
public final class MaidCapability extends LivingAnimatable<LivingEntity> {
    private static final Map<UUID, MaidCapability> CACHE = new ConcurrentHashMap<>();

    @Nullable
    private Struct serverVars;
    private String rouletteAnimation = "";
    private boolean cloudModelStateApplied;
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
    public void applyCloudState(String modelId, String textureId) {
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
                && this.rouletteAnimation.equals(animation)) return;
        awaitAsyncResult();
        var values = new Int2FloatOpenHashMap();
        variables.forEach((name, value) -> values.put(
            com.micaftic.morpher.geckolib3.core.molang.util.StringPool.computeIfAbsent(name), value.floatValue()));
        this.serverVars = new Int2FloatOpenHashMapStruct(values);
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
        try {
            executeExpression(GeckoLibCache.parseSimpleExpression(expression), true, false, null);
        } catch (ParseException e) {
            YesSteveModel.LOGGER.error("Failed to execute maid molang " + expression, e);
        }
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
    public GeoEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault) {
        return new TexturedModelWrapper(modelAssembly, isDefault, true, true, 600);
    }

    @Nullable
    public Struct getServerVarContainer() {
        return this.serverVars;
    }

    @Override
    public void setupAnim(float seekTime, boolean firstPerson) {
        super.setupAnim(seekTime, firstPerson);
        getEvaluationContext().setRoamingProperties(this.serverVars);
    }

    @Override
    public boolean shouldSkipAnimation(AnimationEvent<?> event) {
        return event.isFirstPerson();
    }
}
