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
    private static final java.util.Map<Entity, java.lang.ref.WeakReference<VehicleCapability>> STORE = new java.util.WeakHashMap<>();

    public static synchronized Optional<VehicleCapability> get(Entity entity) {
        if (!entity.level().isClientSide()) return Optional.empty();
        var reference = STORE.get(entity);
        var cap = reference == null ? null : reference.get();
        if (cap == null) { cap = new VehicleCapability(entity); STORE.put(entity, new java.lang.ref.WeakReference<>(cap)); }
        return Optional.of(cap);
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
