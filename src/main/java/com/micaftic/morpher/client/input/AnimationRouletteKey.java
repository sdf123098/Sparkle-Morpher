package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.event.AnimationLockEvent;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;
import com.micaftic.morpher.core.architectury.event.EventResult;
import com.micaftic.morpher.core.architectury.event.events.client.ClientRawInputEvent;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.InputUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Binds the {@code key.sparkle_morpher.animation_roulette.desc} and
 * {@code key.sparkle_morpher.lock_roulette.desc} keys to the unified
 * roulette.
 *
 * <p>Press behavior:
 * <ul>
 *   <li>Roulette key — open the {@link UnifiedRouletteScreen}; if it is
 *       already open, close it. Falls back to TLM maid chat when a
 *       maid is in front of the player.</li>
 *   <li>Lock key — toggle {@link AnimationLockEvent} regardless of
 *       whether the roulette is open.</li>
 * </ul>
 */
public final class AnimationRouletteKey {

    public static final KeyMapping KEY_ROULETTE = KeyMappingFactory.createInGameNone(
            "key.sparkle_morpher.animation_roulette.desc", InputConstants.Type.KEYSYM, 90, "key.category.sparkle_morpher");

    public static final KeyMapping KEY_LOCK = KeyMappingFactory.createInGameAlt(
            "key.sparkle_morpher.lock_roulette.desc", InputConstants.Type.KEYSYM, 76, "key.category.sparkle_morpher");

    private AnimationRouletteKey() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) return;
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) -> {
            if (!YesSteveModel.isAvailable() || !InputUtil.isPlayerReady() || action != 1) return EventResult.pass();
            if (InputUtil.isKeyPressed(keyCode, scanCode, KEY_LOCK)) {
                AnimationLockEvent.toggleLock();
                return EventResult.interruptFalse();
            }
            if (InputUtil.isKeyPressed(keyCode, scanCode, KEY_ROULETTE)) {
                if (NetworkHandler.isClientConnected() && !ServerConfig.CAN_SWITCH_MODEL.get()) {
                    return EventResult.pass();
                }
                handleRoulettePress();
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
    }

    private static void handleRoulettePress() {
        if (TouhouLittleMaidCompat.isMaidChatAvailable()) {
            TouhouLittleMaidCompat.openMaidChat();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerCapability.get(mc.player).ifPresent(cap -> {
            String modelId = cap.getModelId();
            ModelAssembly modelAssembly = cap.getModelAssembly();
            if (modelAssembly == null || modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
                return;
            }
            if (mc.screen == null) {
                mc.setScreen(new UnifiedRouletteScreen(modelId, modelAssembly, cap));
            } else if (mc.screen instanceof UnifiedRouletteScreen) {
                mc.setScreen(null);
            }
        });
    }
}
