package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.*;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.InputUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;

@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class PlayerModelToggleKey {
    public static final KeyMapping KEY_MAPPING = KeyMappingFactory.createInGameAlt("key.sparkle_morpher.player_model.desc", InputConstants.Type.KEYSYM, 89, "key.category.sparkle_morpher");
    private PlayerModelToggleKey() {}
    @SubscribeEvent public static void onKey(InputEvent.Key event) {
        if (PlatformAPI.isServer()) return;
        if (InputUtil.isPlayerReady() && event.getAction() == 1 && InputUtil.isKeyPressed(event.getKey(), event.getScanCode(), KEY_MAPPING)) {
            if (!YesSteveModel.isAvailable()) { YesSteveModel.sendUnavailableMessage(); return; }
            if (NetworkHandler.isClientConnected() && !ServerConfig.CAN_SWITCH_MODEL.get()) Minecraft.getInstance().setScreen(new ExtraPlayerConfigScreen(null));
            else Minecraft.getInstance().setScreen(new PlayerModelScreen());
        }
    }
}