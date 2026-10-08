package com.micaftic.morpher.client.input;

import com.micaftic.morpher.core.api.client.InputCodeAdapter;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.architectury.event.EventResult;
import com.micaftic.morpher.core.architectury.event.events.client.ClientRawInputEvent;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;

/** Shift+Y entry point for the independent fake-player manager GUI. */
public final class FakePlayerModelKey {

    private static final int KEY_Y = InputCodeAdapter.SDL_SCANCODE_Y;
    private static final int SDL_KMOD_SHIFT = 0x0003;

    private FakePlayerModelKey() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) ->
                onKeyInput(action, keyCode, modifiers) ? EventResult.interruptFalse() : EventResult.pass());
    }

    private static boolean onKeyInput(int action, int keyCode, int modifiers) {
        if (action != 1 || keyCode != KEY_Y || (modifiers & SDL_KMOD_SHIFT) == 0 || !InputUtil.isPlayerReady()) {
            return false;
        }
        if (!YesSteveModel.isAvailable()) {
            YesSteveModel.sendUnavailableMessage();
            return true;
        }
        try {
            Class<?> screen = Class.forName("com.micaftic.morpher.client.gui.FakePlayerManagerScreen");
            screen.getMethod("open").invoke(null);
        } catch (ReflectiveOperationException exception) {
            YesSteveModel.LOGGER.warn("[SM] Failed to open fake-player manager GUI", exception);
            Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("SPM: 假人管理 GUI 当前不可用。"));
        }
        return true;
    }
}
