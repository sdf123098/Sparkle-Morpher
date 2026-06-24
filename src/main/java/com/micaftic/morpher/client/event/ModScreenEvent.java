package com.micaftic.morpher.client.event;

import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.client.gui.PlayerModelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@OnlyIn(Dist.CLIENT)
public class ModScreenEvent {
    public static final String IMC_METHOD = "DownloadScreen";

    @Nullable
    private static Screen receivedScreen;

    private ModScreenEvent() {
    }

    public static void setReceivedScreen(@Nullable Screen screen) {
        receivedScreen = screen;
    }

    public static void openScreen(PlayerModelScreen modelScreen) {
        Minecraft.getInstance().setScreen(Objects.requireNonNullElseGet(receivedScreen, ModernPlayerModelScreen::downloads));
    }
}