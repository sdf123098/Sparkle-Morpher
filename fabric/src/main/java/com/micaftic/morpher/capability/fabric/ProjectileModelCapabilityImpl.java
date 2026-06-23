package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.ProjectileModelCapability;
import com.micaftic.morpher.fabric.YsmComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Optional;

public final class ProjectileModelCapabilityImpl {

    private ProjectileModelCapabilityImpl() {
    }

    public static Optional<ProjectileModelCapability> get(Entity entity) {
        if (!(entity instanceof Projectile)) {
            return Optional.empty();
        }
        ProjectileModelComponent component = YsmComponents.PROJECTILE_MODEL.getNullable(entity);
        return component == null ? Optional.empty() : Optional.of(component.capability());
    }

    public static Optional<ProjectileModelCapability> get(Projectile projectile) {
        ProjectileModelComponent component = YsmComponents.PROJECTILE_MODEL.getNullable(projectile);
        return component == null ? Optional.empty() : Optional.of(component.capability());
    }
}
