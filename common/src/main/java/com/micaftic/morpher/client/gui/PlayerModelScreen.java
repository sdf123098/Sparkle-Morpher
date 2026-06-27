package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Compatibility entry for integrations that still instantiate the legacy model screen.
 */
@Deprecated(forRemoval = false)
public class PlayerModelScreen extends Screen {

    public PlayerModelScreen() {
        super(Component.translatable("key.sparkle_morpher.player_model.desc"));
    }

    @Override
    protected void init() {
        InputUtil.setScreen(new ModernPlayerModelScreen());
    }

    public boolean shouldCloseWithToggleKey() {
        return true;
    }
}
