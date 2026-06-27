package com.micaftic.morpher.capability.fabric.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.VehicleCapability;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class VehicleCapabilityClientStore {

    private static final ConcurrentMap<UUID, VehicleCapability> STORE = new ConcurrentHashMap<>();

    private VehicleCapabilityClientStore() {
    }

    public static Optional<VehicleCapability> get(Entity entity) {
        return Optional.of(STORE.computeIfAbsent(entity.getUUID(), uuid -> new VehicleCapability(entity)));
    }

    public static void clear() {
        clear("manual");
    }

    public static void clear(String reason) {
        int size = STORE.size();
        STORE.clear();
        if (size > 0) {
            YesSteveModel.LOGGER.info("[SM] Cleared {} Fabric vehicle capability entries due to {}", size, reason);
        }
    }
}
