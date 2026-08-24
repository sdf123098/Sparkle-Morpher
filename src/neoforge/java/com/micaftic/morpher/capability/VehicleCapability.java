package com.micaftic.morpher.capability;

import com.micaftic.morpher.client.entity.GeckoVehicleEntity;
import com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.neoforged.api.distmarker.Dist;import net.neoforged.api.distmarker.OnlyIn;import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class VehicleCapability extends GeckoVehicleEntity {

        public static Optional<VehicleCapability> get(Entity entity) {
        return Optional.of(new VehicleCapability(entity));
    }

    @Nullable
    private Int2FloatOpenHashMapStruct floatProperties;

    public VehicleCapability(Entity entity) {
        super(entity);
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