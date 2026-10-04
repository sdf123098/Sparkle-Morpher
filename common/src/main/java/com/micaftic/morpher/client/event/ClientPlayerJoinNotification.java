package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.core.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.player.LocalPlayer;

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
        notified = true;
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return;
        }
        if (PrivacyMode.isActive()) {
            ClientModelManager.enterPrivacyMode();
            return;
        }
        // 懒加载模式下，冷启动时模型目录尚未建立；先扫描目录，再恢复上次选择。
        ClientModelManager.reloadLocalModels(error -> ClientModelManager.restorePersistedModelSelection());
    }

    private static void onPlayerQuit(LocalPlayer player) {
        boolean reloadLocalModels = notified && YesSteveModel.isAvailable();
        notified = false;
        com.micaftic.morpher.cloud.client.CloudPlayerModelSync.disconnect();
        PrivacyMode.endSession();
        ClientModelManager.resetSync();
        if (reloadLocalModels) {
            ClientModelManager.reloadLocalModels(null);
        }
    }
}
