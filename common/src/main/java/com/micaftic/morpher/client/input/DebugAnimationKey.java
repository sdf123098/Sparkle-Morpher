package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.renderer.AnimationDebugOverlay;
import com.micaftic.morpher.util.InputUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.micaftic.morpher.core.architectury.event.EventResult;
import com.micaftic.morpher.core.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.KeyMapping;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;

public final class DebugAnimationKey {

    public static final KeyMapping KEY_MAPPING = KeyMappingFactory.createInGameAlt("key.sparkle_morpher.debug_animation.desc", InputConstants.Type.KEYSYM, 66, "key.category.sparkle_morpher");

    private DebugAnimationKey() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) -> {
            if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && action == 1 && InputUtil.isKeyPressed(keyCode, scanCode, modifiers, KEY_MAPPING)) {
                handlePress();
            }
            return EventResult.pass();
        });
        ClientRawInputEvent.MOUSE_CLICKED_PRE.register((client, button, action, modifiers) -> {
            if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && action == 1 && InputUtil.isMousePressed(button, modifiers, KEY_MAPPING)) {
                handlePress();
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
    }

    private static void handlePress() {
        if (!AnimationDebugOverlay.isDebugActive()) {
            AnimationDebugOverlay.tryUpdateFromHitResult();
        } else {
            AnimationDebugOverlay.clearActiveModel();
        }
    }
}
