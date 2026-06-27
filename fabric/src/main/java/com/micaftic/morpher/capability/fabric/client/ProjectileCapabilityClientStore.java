package com.micaftic.morpher.capability.fabric.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.ProjectileCapability;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ProjectileCapabilityClientStore {

    private static final ConcurrentMap<UUID, ProjectileCapability> STORE = new ConcurrentHashMap<>();

    private ProjectileCapabilityClientStore() {
    }

    public static Optional<ProjectileCapability> get(Projectile projectile) {
        return Optional.of(STORE.computeIfAbsent(projectile.getUUID(), uuid -> new ProjectileCapability(projectile)));
    }

    public static void clear() {
        clear("manual");
    }

    public static void clear(String reason) {
        int size = STORE.size();
        STORE.clear();
        if (size > 0) {
            YesSteveModel.LOGGER.info("[SM][Lifecycle] event=capabilityClientStoreClear store=projectile reason={} size={}", reason, size);
        }
    }
}
