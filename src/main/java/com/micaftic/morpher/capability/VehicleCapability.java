package com.micaftic.morpher.capability;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.neoforge.NeoForgeCapabilityTypes;
import com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
public class VehicleCapability extends GeckoVehicleEntity {

    public static Optional<VehicleCapability> get(Entity entity) {
        return NeoForgeCapabilityTypes.vehicleRender(entity);
    }

    @Nullable
    private Int2FloatOpenHashMapStruct floatProperties;

    public VehicleCapability(Entity entity) {
        super(entity);
    }

    @Override
    protected void refreshModel() {
        ClientModelManager.getModelContext(this.getModelId()).ifPresent(assembly -> {
            if (this.renderShape == null || this.renderShape.isDefault || assembly != this.renderShape.context) {
                this.renderShape = buildRenderShape(assembly, false);
            }
        });
        if (this.renderShape != null) {
            if ((this.renderShape.context != this.modelAssembly || this.renderShape.isDefault != this.loaded) && this.renderShape.isValid()) {
                this.modelAssembly = this.renderShape.context;
                this.loaded = this.renderShape.isDefault;
                onModelLoaded(this.modelAssembly);
                initAnimationControllers(getAnimationProcessor(), this.modelAssembly.getExpressionCache().getEvents());
                return;
            }
            return;
        }
        if (this.modelAssembly != null) {
            clearModel();
        }
    }

    public void setOwnerModelId(String str) {
        setModelId(str);
        markModelInitialized();
    }

    public void setFloatMap(@NotNull Int2FloatOpenHashMap int2FloatOpenHashMap) {
        this.floatProperties = new Int2FloatOpenHashMapStruct(int2FloatOpenHashMap);
    }

    public void updateFloatMap(@NotNull Int2FloatMap int2FloatMap) {
        if (this.floatProperties == null) {
            this.floatProperties = new Int2FloatOpenHashMapStruct(new Int2FloatOpenHashMap());
        }
        this.floatProperties.merge(int2FloatMap);
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        super.setupAnim(seekTime, isFirstPerson);
        getEvaluationContext().setRoamingProperties(this.floatProperties);
    }
}
