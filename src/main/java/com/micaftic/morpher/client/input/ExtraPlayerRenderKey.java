package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ExtraPlayerRenderScreen;
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
public final class ExtraPlayerRenderKey {
    public static final KeyMapping KEY_MAPPING = KeyMappingFactory.createInGameNone("key.sparkle_morpher.open_extra_player_render.desc", InputConstants.Type.KEYSYM, 80, "key.category.sparkle_morpher");
    private ExtraPlayerRenderKey() {} public static void register() {}
    @SubscribeEvent public static void onKey(InputEvent.Key event) { if (!PlatformAPI.isServer() && InputUtil.isPlayerReady() && event.getAction() == 1 && InputUtil.isKeyPressed(event.getKey(), event.getScanCode(), KEY_MAPPING) && YesSteveModel.isAvailable()) Minecraft.getInstance().setScreen(new ExtraPlayerRenderScreen()); }
}
