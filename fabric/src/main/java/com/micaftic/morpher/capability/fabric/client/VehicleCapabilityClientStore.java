package com.micaftic.morpher.capability.fabric.client;

import com.micaftic.morpher.capability.VehicleCapability;
import net.minecraft.world.entity.Entity;
import com.micaftic.morpher.YesSteveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class VehicleCapabilityClientStore {

    private static final ConcurrentMap<UUID, VehicleCapability> STORE = new ConcurrentHashMap<>();

    /** 全量清理的时间节流：STORE 超过阈值时最多每秒扫描一次，避免渲染热路径 O(n²)。 */
    private static final AtomicLong LAST_CLEANUP_NANOS = new AtomicLong();

    private VehicleCapabilityClientStore() {
    }

    public static Optional<VehicleCapability> get(Entity entity) {
        if (STORE.size() > 500 && System.nanoTime() - LAST_CLEANUP_NANOS.get() > 1_000_000_000L) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                STORE.values().removeIf(cap -> cap.entity == null || level.getEntity(cap.entity.getId()) != cap.entity);
                LAST_CLEANUP_NANOS.set(System.nanoTime());
            }
        }
        return Optional.of(STORE.computeIfAbsent(entity.getUUID(), uuid -> new VehicleCapability(entity)));
    }

    public static void clear() {
        clear("manual");
    }

    public static void clear(String reason) {
        int size = STORE.size();
        STORE.clear();
        if (size > 0) {
            YesSteveModel.LOGGER.info("[SM][Lifecycle] event=capabilityClientStoreClear store=vehicle reason={} size={}", reason, size);
        }
    }
}
