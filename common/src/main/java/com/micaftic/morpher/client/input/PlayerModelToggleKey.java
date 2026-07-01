package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
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
            return onKeyInput(action, keyCode, scanCode, modifiers) ? EventResult.interruptFalse() : EventResult.pass();
        });
        ClientRawInputEvent.MOUSE_CLICKED_PRE.register((client, button, action, modifiers) -> {
            return onMouseInput(action, button) ? EventResult.interruptFalse() : EventResult.pass();
        });
    }

    private static boolean onKeyInput(int action, int keyCode, int scanCode, int modifiers) {
        if (action != 1 || !InputUtil.isKeyPressed(keyCode, scanCode, modifiers, KEY_MAPPING)) {
            return false;
        }
        return openModelScreen();
    }

    private static boolean onMouseInput(int action, int button) {
        if (action != 1 || !InputUtil.isMousePressed(button, KEY_MAPPING)) {
            return false;
        }
        return openModelScreen();
    }

    private static boolean openModelScreen() {
        if (!InputUtil.isPlayerReady()) {
            return false;
        }
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return true;
        }
        if (NetworkHandler.isClientConnected() && !ServerConfig.CAN_SWITCH_MODEL.get()) {
            Minecraft.getInstance().setScreen(ModernPlayerModelScreen.settings());
        } else {
            Minecraft.getInstance().setScreen(new ModernPlayerModelScreen());
        }
        return true;
    }
}
