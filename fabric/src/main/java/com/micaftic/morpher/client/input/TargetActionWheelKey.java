package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.TargetActionSelectionScreen;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.KeyMapping;

import com.micaftic.morpher.util.InputUtil;

/** Opens the unified fake-player/maid target action selector. */
public final class TargetActionWheelKey {
    public static final KeyMapping KEY_MAPPING = KeyMappingFactory.createInGameNone(
            "key.sparkle_morpher.target_action_wheel.desc",
            InputConstants.Type.KEYSYM, 71, "key.category.sparkle_morpher");

    private TargetActionWheelKey() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) ->
                onKeyInput(action, keyCode, scanCode, modifiers)
                        ? EventResult.interruptFalse() : EventResult.pass());
    }

    private static boolean onKeyInput(int action, int keyCode, int scanCode, int modifiers) {
        if (action != 1 || !YesSteveModel.isAvailable() || !InputUtil.isPlayerReady()
                || !InputUtil.isKeyPressed(keyCode, scanCode, modifiers, KEY_MAPPING)) {
            return false;
        }
        TargetActionSelectionScreen.open();
        return true;
    }
}

