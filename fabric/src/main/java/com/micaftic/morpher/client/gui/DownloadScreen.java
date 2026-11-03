package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Compatibility entry for integrations that still request the old download screen.
 */
@Deprecated(forRemoval = false)
public class DownloadScreen extends Screen {

    public DownloadScreen(PlayerModelScreen modelScreen) {
        this(modelScreen, null);
    }

    public DownloadScreen(PlayerModelScreen modelScreen, Screen resourceStationScreen) {
        super(Component.translatable("gui.sparkle_morpher.resource_station.downloads"));
    }

    @Override
    protected void init() {
        InputUtil.setScreen(ModernPlayerModelScreen.downloads());
    }
}
