package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.core.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
import java.util.concurrent.Executor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import com.micaftic.morpher.core.api.PlatformAPI;

public final class ClientPlayerJoinNotification {

    private static boolean notified = false;

    private ClientPlayerJoinNotification() {
    }

    public static void register() {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(ClientPlayerJoinNotification::onPlayerJoin);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(ClientPlayerJoinNotification::onPlayerQuit);
    }

    private static void onPlayerJoin(LocalPlayer player) {
        if (notified) {
            return;
        }
        ClientModelManager.runPendingModelCallback();
        ClientModelManager.restorePersistedModelSelection();
        notified = true;
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return;
        }
        if (Minecraft.getInstance().isLocalServer()) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(60000L);
                ((Executor) Minecraft.getInstance()).execute(() -> {
                    LocalPlayer localPlayer = Minecraft.getInstance().player;
                    if (localPlayer != null && localPlayer.connection.isAcceptingMessages() && !NetworkHandler.isConnectionValid(localPlayer.connection.getConnection())) {
                        localPlayer.sendSystemMessage(Component.translatable("message.sparkle_morpher.client.server_not_found"));
                    }
                });
            } catch (InterruptedException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void onPlayerQuit(LocalPlayer player) {
        if (notified) {
            notified = false;
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            ClientModelManager.resetSync();
            ClientModelManager.reloadLocalModels(null);
        }
    }
}
