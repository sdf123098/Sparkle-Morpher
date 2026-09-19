package com.micaftic.morpher.fakeplayer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.micaftic.morpher.core.target.TargetType;

import java.util.Locale;

/**
 * Optional fake-player classification shared by the GUI and the server packet.
 *
 * <p>This deliberately does not import Carpet or any NeoForge fork.  The
 * upstream Fabric Carpet implementation and its NeoForge ports all expose a
 * player-shaped entity, so the compatibility boundary is based on the runtime
 * provider class/connection name and can be extended without a hard dependency.</p>
 */
public final class FakePlayerTargetRules {

    private FakePlayerTargetRules() {
    }

    public static boolean isFakePlayer(ServerPlayer player) {
        return targetType(player) == TargetType.FAKE_PLAYER;
    }

    public static TargetType targetType(ServerPlayer player) {
        if (player == null) {
            return TargetType.UNKNOWN;
        }
        String entityClass = player.getClass().getName();
        String connectionClass = player.connection == null ? "" : player.connection.getClass().getName();
        return looksLikeFakeProvider(entityClass) || looksLikeFakeProvider(connectionClass)
                ? TargetType.FAKE_PLAYER : TargetType.PLAYER;
    }

    public static boolean isLikelyFakeClientEntity(Entity entity) {
        return entity != null && looksLikeFakeProvider(entity.getClass().getName());
    }

    public static String providerId(ServerPlayer player) {
        if (player == null) {
            return "unknown";
        }
        String names = (player.getClass().getName() + " "
                + (player.connection == null ? "" : player.connection.getClass().getName())).toLowerCase(Locale.ROOT);
        if (names.contains("siliconedoll") || names.contains("rollinggate")) {
            return "rolling_gate_silicone_dolls";
        }
        if (names.contains("carpet")) {
            return "carpet_fork";
        }
        return "fake_player_provider";
    }

    static boolean looksLikeFakeProvider(String className) {
        String normalized = className == null ? "" : className.toLowerCase(Locale.ROOT);
        return normalized.contains("fakeclientconnection")
                || normalized.contains("fake_client_connection")
                || normalized.contains("entityplayermpfake")
                || normalized.contains("fakeplayer")
                || normalized.contains("fake_player")
                || normalized.contains("siliconedoll")
                || normalized.contains("rollinggate")
                || normalized.contains("carpetbot")
                || normalized.contains("botplayer");
    }
}
