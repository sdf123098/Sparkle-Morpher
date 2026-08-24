package com.micaftic.morpher.client;

import com.micaftic.morpher.core.api.network.state.PrivacyState;
import com.micaftic.morpher.core.config.ConfigPolicies;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Keeps the current client session isolated from Sparkle Morpher server state.
 *
 * <p>Once enabled for a connected session it remains active until disconnect,
 * even if the config is switched off. This prevents a half-synchronised session
 * from unexpectedly resuming outbound traffic.</p>
 *
 * <p>R9.2：状态语义归 {@link PrivacyState}（sessionActive / configured 双标志），
 * 本类只做客户端适配——从 GeneralConfig 读取配置并同步进 PrivacyState，以及执行
 * 进入/退出隐私模式的客户端副作用（提示消息、模型切换）。</p>
 */
@Environment(EnvType.CLIENT)
public final class PrivacyMode {
    private PrivacyMode() {
    }

    public static boolean isConfigured() {
        return ConfigPolicies.privacy().enabled();
    }

    public static boolean isActive() {
        PrivacyState.setConfigured(isConfigured());
        return PrivacyState.isActive();
    }

    public static void beginSession() {
        boolean configured = isConfigured();
        PrivacyState.setConfigured(configured);
        PrivacyState.setSessionActive(configured);
    }

    public static void endSession() {
        PrivacyState.setSessionActive(false);
    }

    public static void onConfigChanged(boolean enabled) {
        PrivacyState.setConfigured(enabled);
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (enabled) {
            PrivacyState.setSessionActive(true);
            if (player != null) {
                ClientModelManager.enterPrivacyMode();
                player.sendSystemMessage(Component.translatable("message.sparkle_morpher.privacy_mode.enabled"));
            }
            return;
        }
        if (player == null) {
            PrivacyState.setSessionActive(false);
        } else if (PrivacyState.isActive()) {
            player.sendSystemMessage(Component.translatable("message.sparkle_morpher.privacy_mode.reconnect"));
        }
    }
}
