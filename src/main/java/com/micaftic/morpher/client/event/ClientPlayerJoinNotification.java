package com.micaftic.morpher.client.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.SmExecutors;
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
        if (notified) return; PrivacyMode.beginSession(); ClientModelManager.runPendingModelCallback(); notified = true;
        if (!YesSteveModel.isAvailable()) { YesSteveModel.sendUnavailableMessage(); return; }
        if (PrivacyMode.isActive()) { ClientModelManager.enterPrivacyMode(); return; }
        // 懒加载模式下，冷启动时模型目录尚未建立；先扫描目录，再恢复上次选择。
        ClientModelManager.reloadLocalModels(error -> ClientModelManager.restorePersistedModelSelection());
        if (Minecraft.getInstance().isLocalServer()) return;
        // R2.1：原裸线程（handshake watchdog + 60s 服务器未响应提示）改为 BACKGROUND 池提交
        SmExecutors.submit(SmExecutors.Pool.BACKGROUND, () -> { try { Thread.sleep(3000L); Minecraft.getInstance().execute(ClientModelManager::markVanillaServerIfNoHandshake); } catch (InterruptedException ignored) {} });
        SmExecutors.submit(SmExecutors.Pool.BACKGROUND, () -> { try { Thread.sleep(60000L); Minecraft.getInstance().execute(() -> { LocalPlayer p = Minecraft.getInstance().player; if (p != null && p.connection.isAcceptingMessages() && !NetworkHandler.isConnectionValid(p.connection.getConnection())) p.sendSystemMessage(Component.translatable("message.sparkle_morpher.client.server_not_found")); }); } catch (InterruptedException ignored) {} });
    }
    @SubscribeEvent public static void onQuit(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        boolean reloadLocalModels = notified && YesSteveModel.isAvailable();
        notified = false;
        PrivacyMode.endSession();
        ClientModelManager.resetSync();
        if (reloadLocalModels) {
            ClientModelManager.reloadLocalModels(null);
        }
    }
}
