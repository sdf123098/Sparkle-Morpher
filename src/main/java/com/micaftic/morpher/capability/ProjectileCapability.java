package com.micaftic.morpher.capability;

import com.micaftic.morpher.client.entity.GeckoProjectileEntity;
import com.micaftic.morpher.neoforge.NeoForgeCapabilityTypes;
import com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
public class ProjectileCapability extends GeckoProjectileEntity {

    public static Optional<ProjectileCapability> get(Entity entity) {
        return NeoForgeCapabilityTypes.projectileRender(entity);
    }

    public static Optional<ProjectileCapability> get(Projectile projectile) {
        return Optional.of(projectile.getData(NeoForgeCapabilityTypes.PROJECTILE_RENDER));
    }

    @Nullable
    private Int2FloatOpenHashMapStruct floatProperties;

    public ProjectileCapability(Projectile projectile) {
        super(projectile);
    }

    public void updateModelId(String str) {
        setModelId(str);
        markModelInitialized();
    }

    public void setFloatProperties(Int2FloatOpenHashMap int2FloatOpenHashMap) {
        if (int2FloatOpenHashMap != null) {
            this.floatProperties = new Int2FloatOpenHashMapStruct(int2FloatOpenHashMap);
        } else {
            this.floatProperties = null;
        }
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        super.setupAnim(seekTime, isFirstPerson);
        getEvaluationContext().setRoamingProperties(this.floatProperties);
    }
}
