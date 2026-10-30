package com.micaftic.morpher.core.compat.tweakeroo;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/** Optional client compatibility; never loads or links Tweakeroo classes. */
public final class TweakerooCompat {
    private static final ClassValue<Boolean> CAMERA_TYPES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if ("fi.dy.masa.tweakeroo.util.CameraEntity".equals(current.getName())) {
                    return true;
                }
            }
            return false;
        }
    };

    private TweakerooCompat() {
    }

    /** The surrogate shares the body's UUID/id but must never own its model state. */
    public static boolean isCameraEntity(Object entity) {
        return entity != null && CAMERA_TYPES.get(entity.getClass());
    }

    public static boolean isSpectatorForRendering(Player player) {
        Minecraft client = Minecraft.getInstance();
        // Tweakeroo temporarily makes LocalPlayer.isSpectator() return true to
        // render the detached camera. Use the real game mode only for its owner.
        if (player == client.player && isCameraEntity(client.getCameraEntity()) && client.gameMode != null) {
            return client.gameMode.getPlayerMode() == GameType.SPECTATOR;
        }
        return player.isSpectator();
    }
}
