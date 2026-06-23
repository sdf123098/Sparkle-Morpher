package com.micaftic.morpher.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import com.micaftic.morpher.core.api.PlatformAPI;

@EventBusSubscriber(modid = com.micaftic.morpher.YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ExtraAnimationKey {
    private ExtraAnimationKey() {}
    public static java.util.List<KeyMapping> getKeyMappings() { return java.util.List.of(); }
    public static void register() {}
    @SubscribeEvent public static void onKey(InputEvent.Key event) {}
}