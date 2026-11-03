package com.micaftic.morpher.util;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import dev.architectury.utils.GameInstance;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.api.PlatformAPI;

public class YSMMessageFormatter {

    private static final String PREFIX = "§6§l【§aYSM§6§l】§r";

    public static Component withPrefix(Component component) {
        return Component.literal(PREFIX).append(component);
    }

    public static boolean isCurrentClientPlayer(Entity entity) {
        return entity != null && !PlatformAPI.isServer() && entity.getUUID().equals(Minecraft.getInstance().getGameProfile().getId());
    }

    public static boolean hasPermission(@Nullable Entity entity, int level) {
        if (entity == null) {
            return false;
        }
        return entity.hasPermissions(level) || isCurrentClientPlayer(entity);
    }

    public static boolean hasCommandPermission(CommandSourceStack commandSourceStack, int level) {
        if (commandSourceStack.hasPermission(level)) {
            return true;
        }
        return commandSourceStack.getEntity() != null && isCurrentClientPlayer(commandSourceStack.getEntity());
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