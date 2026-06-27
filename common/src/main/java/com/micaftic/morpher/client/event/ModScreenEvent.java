package com.micaftic.morpher.client.event;

import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.client.gui.PlayerModelScreen;
import com.micaftic.morpher.util.InputUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Environment(EnvType.CLIENT)
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
        InputUtil.setScreen(Objects.requireNonNullElseGet(receivedScreen, ModernPlayerModelScreen::downloads));
    }
}
