package com.micaftic.morpher.capability.client;

import com.micaftic.morpher.capability.PlayerCapability;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerCapabilityClientStore {
    private static final ConcurrentMap<UUID, PlayerCapability> STORE = new ConcurrentHashMap<>();

    private static final java.util.concurrent.atomic.AtomicLong LAST_CLEANUP_NANOS = new java.util.concurrent.atomic.AtomicLong();

    private PlayerCapabilityClientStore() {
    }

    public static Optional<PlayerCapability> get(Player player) {
        if (!(player instanceof AbstractClientPlayer)) {
            return Optional.empty();
        }
        long now = System.nanoTime();
        if (!STORE.isEmpty() && now - LAST_CLEANUP_NANOS.get() > 1_000_000_000L) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null) {
                STORE.values().removeIf(cap -> cap.entity == null || level.getEntity(cap.entity.getId()) != cap.entity);
                LAST_CLEANUP_NANOS.set(now);
            }
        }
        UUID uuid = player.getUUID();
        PlayerCapability existing = STORE.get(uuid);
        if (existing != null && existing.entity == player) {
            return Optional.of(existing);
        }
        PlayerCapability fresh = new PlayerCapability(player);
        STORE.put(uuid, fresh);
        return Optional.of(fresh);
    }

    public static void clear() {
        STORE.clear();
    }
}
