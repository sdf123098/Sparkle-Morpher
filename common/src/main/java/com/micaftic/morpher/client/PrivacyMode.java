package com.micaftic.morpher.client;

import com.micaftic.morpher.config.GeneralConfig;
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
 */
@Environment(EnvType.CLIENT)
public final class PrivacyMode {
    private static volatile boolean sessionActive;

    private PrivacyMode() {
    }

    public static boolean isConfigured() {
        return GeneralConfig.safeGet(GeneralConfig.PRIVACY_MODE, false);
    }

    public static boolean isActive() {
        return sessionActive || isConfigured();
    }

    public static void beginSession() {
        sessionActive = isConfigured();
    }

    public static void endSession() {
        sessionActive = false;
    }

    public static void onConfigChanged(boolean enabled) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (enabled) {
            sessionActive = true;
            if (player != null) {
                ClientModelManager.enterPrivacyMode();
                player.sendSystemMessage(Component.translatable("message.sparkle_morpher.privacy_mode.enabled"));
            }
            return;
        }
        if (player == null) {
            sessionActive = false;
        } else if (sessionActive) {
            player.sendSystemMessage(Component.translatable("message.sparkle_morpher.privacy_mode.reconnect"));
        }
    }
}
