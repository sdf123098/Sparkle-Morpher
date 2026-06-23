package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ExtraPlayerConfigScreen;
import com.micaftic.morpher.client.gui.PlayerModelScreen;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.InputUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.micaftic.morpher.core.architectury.event.EventResult;
import com.micaftic.morpher.core.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;

public final class PlayerModelToggleKey {

    public static final KeyMapping KEY_MAPPING = KeyMappingFactory.createInGameAlt("key.sparkle_morpher.player_model.desc", InputConstants.Type.KEYSYM, 89, "key.category.sparkle_morpher");

    private PlayerModelToggleKey() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) -> {
            return onKeyInput(action, keyCode, scanCode) ? EventResult.interruptFalse() : EventResult.pass();
        });
    }

    private static boolean onKeyInput(int action, int keyCode, int scanCode) {
        if (action != 1 || !InputUtil.isKeyPressed(keyCode, scanCode, KEY_MAPPING)) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PlayerModelScreen screen) {
            if (screen.shouldCloseWithToggleKey()) {
                screen.onClose();
                return true;
            }
            return false;
        }
        if (!InputUtil.isPlayerReady()) {
            return false;
        }
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return true;
        }
        if (NetworkHandler.isClientConnected() && !ServerConfig.CAN_SWITCH_MODEL.get()) {
            minecraft.setScreen(new ExtraPlayerConfigScreen(null));
        } else {
            minecraft.setScreen(new PlayerModelScreen());
        }
        return true;
    }
}
