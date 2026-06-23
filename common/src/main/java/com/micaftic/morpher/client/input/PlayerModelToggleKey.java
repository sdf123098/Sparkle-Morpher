package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ExtraPlayerConfigScreen;
import com.micaftic.morpher.client.gui.PlayerModelScreen;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.InputUtil;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
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
            onKeyInput(action, keyCode, scanCode);
            return EventResult.pass();
        });
    }

    private static void onKeyInput(int action, int keyCode, int scanCode) {
        if (InputUtil.isPlayerReady() && action == 1 && InputUtil.isKeyPressed(keyCode, scanCode, KEY_MAPPING)) {
            if (!YesSteveModel.isAvailable()) {
                YesSteveModel.sendUnavailableMessage();
                return;
            }
            if (NetworkHandler.isClientConnected() && !ServerConfig.CAN_SWITCH_MODEL.get()) {
                Minecraft.getInstance().setScreen(new ExtraPlayerConfigScreen(null));
            } else {
                Minecraft.getInstance().setScreen(new PlayerModelScreen());
            }
        }
    }
}