package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.ProjectileCapability;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Optional;
import com.micaftic.morpher.capability.fabric.client.ProjectileCapabilityClientStore;

public final class ProjectileCapabilityImpl {

    private ProjectileCapabilityImpl() {
    }

    public static Optional<ProjectileCapability> get(Entity entity) {
        if (!(entity instanceof Projectile projectile)) {
            return Optional.empty();
        }
        return get(projectile);
    }

    public static Optional<ProjectileCapability> get(Projectile projectile) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT || !projectile.level().isClientSide()) {
            return Optional.empty();
        }
        return ProjectileCapabilityClientStore.get(projectile);
    }
}
