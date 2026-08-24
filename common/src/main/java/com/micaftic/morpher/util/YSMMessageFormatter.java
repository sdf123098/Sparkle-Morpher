package com.micaftic.morpher.util;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import com.micaftic.morpher.core.architectury.utils.GameInstance;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.api.PlatformAPI;

public class YSMMessageFormatter {

    private static final String PREFIX = "§6§l【§aYSM§6§l】§r";

    public static Component withPrefix(Component component) {
        return Component.literal(PREFIX).append(component);
    }

    public static boolean isCurrentClientPlayer(Entity entity) {
        return entity != null && !PlatformAPI.isServer() && entity.getUUID().equals(Minecraft.getInstance().getUser().getProfileId());
    }

    public static boolean hasPermission(@Nullable Entity entity, int level) {
        if (entity == null) {
            return false;
        }
        if (isCurrentClientPlayer(entity)) {
            return true;
        }
        if (entity instanceof ServerPlayer serverPlayer) {
            return hasRequiredPermission(serverPlayer.createCommandSourceStack().permissions(), level);
        }
        return false;
    }

    public static boolean hasCommandPermission(CommandSourceStack commandSourceStack, int level) {
        if (hasRequiredPermission(commandSourceStack.permissions(), level)) {
            return true;
        }
        return commandSourceStack.getEntity() != null && isCurrentClientPlayer(commandSourceStack.getEntity());
    }

    private static boolean hasRequiredPermission(PermissionSet permissions, int level) {
        if (permissions == PermissionSet.ALL_PERMISSIONS) {
            return true;
        }
        if (permissions instanceof LevelBasedPermissionSet levelBasedPermissionSet) {
            return levelBasedPermissionSet.level().isEqualOrHigherThan(PermissionLevel.byId(level));
        }
        return level <= 0;
    }

    public static void sendServerMessage(@Nullable CommandSourceStack commandSourceStack, Component component, boolean broadcastToOps) {
        MinecraftServer currentServer = GameInstance.getServer();
        if (currentServer == null) {
            return;
        }
        currentServer.execute(() -> {
            ServerPlayer player;
            CommandSourceStack sourceStack = null;
            if (commandSourceStack != null && (commandSourceStack.getEntity() instanceof ServerPlayer) && (player = currentServer.getPlayerList().getPlayer(commandSourceStack.getEntity().getUUID())) != null) {
                sourceStack = player.createCommandSourceStack();
            }
            if (sourceStack == null) {
                sourceStack = currentServer.createCommandSourceStack();
            }
            sourceStack.sendSuccess(() -> component, broadcastToOps);
        });
    }
}
