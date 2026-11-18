package com.micaftic.morpher.capability;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.animation.molang.struct.RoamingStruct;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
public class VehicleCapability extends SpmVehicleEntity implements com.micaftic.morpher.client.entity.RoamingPropertyHolder {

    private static final java.util.Map<Entity, java.lang.ref.WeakReference<VehicleCapability>> STORE = new java.util.WeakHashMap<>();

    public static synchronized Optional<VehicleCapability> get(Entity entity) {
        if (!entity.level().isClientSide()) return Optional.empty();
        var reference = STORE.get(entity);
        var cap = reference == null ? null : reference.get();
        if (cap == null) { cap = new VehicleCapability(entity); STORE.put(entity, new java.lang.ref.WeakReference<>(cap)); }
        return Optional.of(cap);
    }

    @Nullable
    private RoamingStruct floatProperties;

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
        this.floatProperties = RoamingStruct.readOnly(int2FloatOpenHashMap);
    }

    public void updateFloatMap(@NotNull Int2FloatMap int2FloatMap) {
        if (this.floatProperties == null) {
            this.floatProperties = RoamingStruct.readOnly(new Int2FloatOpenHashMap());
        }
        this.floatProperties.merge(int2FloatMap);
    }

    @Override
    @Nullable
    public com.micaftic.morpher.client.animation.molang.struct.RoamingStruct getServerVarContainer() {
        return this.floatProperties;
    }
}
