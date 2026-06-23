package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientPlayerJoinNotification {
    private static boolean notified = false;
    private ClientPlayerJoinNotification() {}
    @SubscribeEvent public static void onJoin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        if (notified) return; ClientModelManager.runPendingModelCallback(); ClientModelManager.restorePersistedModelSelection(); notified = true;
        if (!YesSteveModel.isAvailable()) { YesSteveModel.sendUnavailableMessage(); return; }
        if (Minecraft.getInstance().isLocalServer()) return;
        Thread t = new Thread(() -> { try { Thread.sleep(60000L); Minecraft.getInstance().execute(() -> { LocalPlayer p = Minecraft.getInstance().player; if (p != null && p.connection.isAcceptingMessages() && !NetworkHandler.isConnectionValid(p.connection.getConnection())) p.sendSystemMessage(Component.translatable("message.sparkle_morpher.client.server_not_found")); }); } catch (InterruptedException ignored) {} });
        t.setDaemon(true); t.start();
    }
    @SubscribeEvent public static void onQuit(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (notified) { notified = false; if (YesSteveModel.isAvailable()) ClientModelManager.resetSync(); }
    }
}