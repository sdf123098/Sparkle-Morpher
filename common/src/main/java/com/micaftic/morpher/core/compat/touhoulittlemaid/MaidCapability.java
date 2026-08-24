package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.VehicleModelCapability;
import com.micaftic.morpher.client.entity.GeoEntity;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import com.micaftic.morpher.molang.parser.ParseException;
import com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct;
import com.micaftic.morpher.molang.runtime.Struct;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MaidCapability extends LivingAnimatable<LivingEntity> {
    private static final Map<UUID, MaidCapability> CACHE = new ConcurrentHashMap<>();

    @Nullable
    private Struct serverVars;
    private String rouletteAnimation = "";
    private boolean officialYsmStateApplied;
    private boolean syncedModelStateApplied;

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

    public void applySyncedState(VehicleModelCapability state, Int2FloatOpenHashMap values) {
        this.officialYsmStateApplied = false;
        this.syncedModelStateApplied = state.isInitialized();
        if (!state.isInitialized()) {
            this.rouletteAnimation = "";
            this.serverVars = null;
            resetModel();
            return;
        }
        this.serverVars = new Int2FloatOpenHashMapStruct(values);
        this.rouletteAnimation = state.getRouletteAnimation();
        initModelWithTexture(state.getOwnerModelId(), state.getOwnerTexture());
    }

    public void syncOfficialYsmState() {
        if (this.syncedModelStateApplied) {
            return;
        }
        if (!TouhouLittleMaidAccess.isYsmModel(this.entity)) {
            if (this.officialYsmStateApplied) {
                this.officialYsmStateApplied = false;
                resetModel();
            }
            return;
        }
    String modelId = TouhouLittleMaidAccess.getYsmModelId(this.entity);
    if (modelId.isBlank()) {
        if (this.officialYsmStateApplied) {
            this.officialYsmStateApplied = false;
            resetModel();
        }
        return;
    }
        this.officialYsmStateApplied = true;
        String texture = TouhouLittleMaidAccess.getYsmModelTexture(this.entity);
        if (!isModelInitialized() || !modelId.equals(getModelId()) || !texture.equals(getCurrentTextureName())) {
            initModelWithTexture(modelId, texture);
        }
    }

    public String getRouletteAnimation() {
        return this.rouletteAnimation;
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
}
