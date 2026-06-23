package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.event.AnimationLockEvent;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.InputUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Binds the {@code key.sparkle_morpher.animation_roulette.desc} and
 * {@code key.sparkle_morpher.lock_roulette.desc} keys to the unified
 * roulette via the NeoForge native input event.
 *
 * <p>This Neo1.21.1 build does not pull in Architectury, so it
 * subscribes to {@link InputEvent.Key} instead of
 * {@code ClientRawInputEvent}. Behavior mirrors the Architectury
 * variants used elsewhere.</p>
 */
@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class AnimationRouletteKey {

    public static final KeyMapping KEY_ROULETTE = KeyMappingFactory.createInGameNone(
            "key.sparkle_morpher.animation_roulette.desc", InputConstants.Type.KEYSYM, 90, "key.category.sparkle_morpher");

    public static final KeyMapping KEY_LOCK = KeyMappingFactory.createInGameAlt(
            "key.sparkle_morpher.lock_roulette.desc", InputConstants.Type.KEYSYM, 76, "key.category.sparkle_morpher");

    private AnimationRouletteKey() {
    }

    /** Kept for symmetry with the other loaders; subscription is automatic. */
    public static void register() {
        if (PlatformAPI.isServer()) return;
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (PlatformAPI.isServer() || !InputUtil.isPlayerReady() || event.getAction() != 1) return;
        if (!YesSteveModel.isAvailable()) return;
        if (InputUtil.isKeyPressed(event.getKey(), event.getScanCode(), KEY_LOCK)) {
            AnimationLockEvent.toggleLock();
            return;
        }
        if (InputUtil.isKeyPressed(event.getKey(), event.getScanCode(), KEY_ROULETTE)) {
            if (NetworkHandler.isClientConnected() && !ServerConfig.CAN_SWITCH_MODEL.get()) return;
            handleRoulettePress();
        }
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
