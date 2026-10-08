package com.micaftic.morpher.capability.fabric.client;

import com.micaftic.morpher.capability.ProjectileCapability;
import net.minecraft.world.entity.projectile.Projectile;
import com.micaftic.morpher.YesSteveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ProjectileCapabilityClientStore {

    private static final ConcurrentMap<UUID, ProjectileCapability> STORE = new ConcurrentHashMap<>();

    /** Unloaded entities are pruned once a second, including small stores. */
    private static final AtomicLong LAST_CLEANUP_NANOS = new AtomicLong();

    private ProjectileCapabilityClientStore() {
    }

    public static Optional<ProjectileCapability> get(Projectile projectile) {
        if (!STORE.isEmpty() && System.nanoTime() - LAST_CLEANUP_NANOS.get() > 1_000_000_000L) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                STORE.values().removeIf(cap -> cap.entity == null || level.getEntity(cap.entity.getId()) != cap.entity);
                LAST_CLEANUP_NANOS.set(System.nanoTime());
            }
        }
        return Optional.of(STORE.compute(projectile.getUUID(), (uuid, existing) ->
                existing != null && existing.entity == projectile ? existing : new ProjectileCapability(projectile)));
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
