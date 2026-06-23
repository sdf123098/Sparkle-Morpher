package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.renderer.AnimationDebugOverlay;
import com.micaftic.morpher.util.InputUtil;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
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
            if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && action == 1 && InputUtil.isKeyPressed(keyCode, scanCode, KEY_MAPPING)) {
                if (!AnimationDebugOverlay.isDebugActive()) {
                    AnimationDebugOverlay.tryUpdateFromHitResult();
                } else {
                    AnimationDebugOverlay.clearActiveModel();
                }
            }
            return EventResult.pass();
        });
    }
}