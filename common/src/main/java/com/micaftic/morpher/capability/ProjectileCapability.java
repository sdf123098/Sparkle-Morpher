package com.micaftic.morpher.capability;

import com.micaftic.morpher.client.entity.GeckoProjectileEntity;
import com.micaftic.morpher.molang.runtime.Int2FloatOpenHashMapStruct;
import dev.architectury.injectables.annotations.ExpectPlatform;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@Environment(EnvType.CLIENT)
public class ProjectileCapability extends GeckoProjectileEntity {

    @ExpectPlatform
    public static Optional<ProjectileCapability> get(Entity entity) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static Optional<ProjectileCapability> get(Projectile projectile) {
        throw new AssertionError();
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