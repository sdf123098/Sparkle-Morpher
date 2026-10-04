package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientPlayerJoinNotification {
    private static boolean notified = false;
    private ClientPlayerJoinNotification() {}
    @SubscribeEvent public static void onJoin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        if (notified) return;
        PrivacyMode.beginSession(); ClientModelManager.runPendingModelCallback(); notified = true;
        if (!YesSteveModel.isAvailable()) { YesSteveModel.sendUnavailableMessage(); return; }
        if (PrivacyMode.isActive()) { ClientModelManager.enterPrivacyMode(); return; }
        ClientModelManager.reloadLocalModels(error -> ClientModelManager.restorePersistedModelSelection());
    }
    @SubscribeEvent public static void onQuit(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
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
