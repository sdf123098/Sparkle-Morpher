package com.micaftic.morpher.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Compatibility entry for callers that still open the legacy config screen.
 */
@Deprecated(forRemoval = false)
public class ExtraPlayerConfigScreen extends Screen {

    public ExtraPlayerConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.sparkle_morpher.settings.title"));
    }

    @Override
    protected void init() {
        Minecraft.getInstance().setScreen(ModernPlayerModelScreen.settings());
    }
}
