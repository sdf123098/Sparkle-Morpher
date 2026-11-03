package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Compatibility entry for callers that still open the legacy resource station screen.
 */
@Deprecated(forRemoval = false)
public class ResourceStationScreen extends Screen {

    public ResourceStationScreen(Screen parentScreen) {
        super(Component.translatable("gui.sparkle_morpher.resource_station.title"));
    }

    @Override
    protected void init() {
        InputUtil.setScreen(ModernPlayerModelScreen.resourceStation());
    }
}
