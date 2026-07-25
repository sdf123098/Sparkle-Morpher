package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.network.NetworkHandler;
import dev.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
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
        PrivacyMode.beginSession();
        ClientModelManager.runPendingModelCallback();
        ClientModelManager.restorePersistedModelSelection();
        notified = true;
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return;
        }
        if (PrivacyMode.isActive()) {
            ClientModelManager.enterPrivacyMode();
            return;
        }
        if (Minecraft.getInstance().isLocalServer()) {
            return;
        }
        Thread handshakeWatchdog = new Thread(() -> {
            try {
                Thread.sleep(3000L);
                Minecraft.getInstance().execute(ClientModelManager::markVanillaServerIfNoHandshake);
            } catch (InterruptedException ignored) {
            }
        });
        handshakeWatchdog.setDaemon(true);
        handshakeWatchdog.start();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(60000L);
                Minecraft.getInstance().execute(() -> {
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
        boolean reloadLocalModels = notified && YesSteveModel.isAvailable();
        notified = false;
        PrivacyMode.endSession();
        ClientModelManager.resetSync();
        if (reloadLocalModels) {
            ClientModelManager.reloadLocalModels(null);
        }
    }
}
