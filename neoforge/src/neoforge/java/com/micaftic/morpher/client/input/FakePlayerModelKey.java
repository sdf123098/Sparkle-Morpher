package com.micaftic.morpher.client.input;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.FakePlayerManagerScreen;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** NeoForge 1.21.1 input adapter for the standalone fake-player manager. */
@EventBusSubscriber(modid = YesSteveModel.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class FakePlayerModelKey {

    private FakePlayerModelKey() {
    }

    public static void register() {
        // NeoForge discovers the handler through @EventBusSubscriber.
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != 1 || event.getKey() != 89 || (event.getModifiers() & 1) == 0
                || !InputUtil.isPlayerReady()) {
            return;
        }
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return;
        }
        FakePlayerManagerScreen.open();
    }
}
