package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.TargetActionSelectionScreen;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** NeoForge 1.21.1 input adapter for the unified target action selector. */
@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class TargetActionWheelKey {
    public static final KeyMapping KEY_MAPPING = KeyMappingFactory.createInGameNone(
            "key.sparkle_morpher.target_action_wheel.desc",
            InputConstants.Type.KEYSYM, 71, "key.category.sparkle_morpher");
    private TargetActionWheelKey() {
    }

    public static void register() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (PlatformAPI.isServer() || event.getAction() != 1
                || !InputUtil.isKeyPressed(event.getKey(), event.getScanCode(), event.getModifiers(), KEY_MAPPING)
                || !InputUtil.isPlayerReady()) {
            return;
        }
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return;
        }
        TargetActionSelectionScreen.open();
    }
}
