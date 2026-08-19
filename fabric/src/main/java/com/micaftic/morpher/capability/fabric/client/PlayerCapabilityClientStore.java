package com.micaftic.morpher.capability.fabric.client;

import com.micaftic.morpher.capability.PlayerCapability;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import com.micaftic.morpher.YesSteveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerCapabilityClientStore {

    private static final ConcurrentMap<UUID, PlayerCapability> STORE = new ConcurrentHashMap<>();

    /** 全量清理的时间节流：STORE 超过阈值时最多每秒扫描一次，避免渲染热路径 O(n²)。 */
    private static final AtomicLong LAST_CLEANUP_NANOS = new AtomicLong();

    private PlayerCapabilityClientStore() {
    }

    public static Optional<PlayerCapability> get(Player player) {
        if (!(player instanceof AbstractClientPlayer)) {
            return Optional.empty();
        }
        if (STORE.size() > 500 && System.nanoTime() - LAST_CLEANUP_NANOS.get() > 1_000_000_000L) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                STORE.values().removeIf(cap -> cap.entity == null || level.getEntity(cap.entity.getId()) != cap.entity);
                LAST_CLEANUP_NANOS.set(System.nanoTime());
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
        clear("manual");
    }

    public static void clear(String reason) {
        int size = STORE.size();
        STORE.clear();
        if (size > 0) {
            YesSteveModel.LOGGER.info("[SM][Lifecycle] event=capabilityClientStoreClear store=player reason={} size={}", reason, size);
        }
    }
}
