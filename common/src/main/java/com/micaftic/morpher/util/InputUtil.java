package com.micaftic.morpher.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;

public class InputUtil {
    public static boolean isKeyPressed(int keyCode, int scanCode, KeyMapping keyMapping) {
        return KeyMappingFactory.isActiveAndMatches(keyMapping, keyCode, scanCode);
    }

    public static boolean isKeyPressed(int keyCode, int scanCode, int modifiers, KeyMapping keyMapping) {
        return KeyMappingFactory.isActiveAndMatches(keyMapping, keyCode, scanCode, modifiers);
    }

    public static boolean isMousePressed(int button, KeyMapping keyMapping) {
        return KeyMappingFactory.isMouseActiveAndMatches(keyMapping, button);
    }

    public static boolean isPlayerReady() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || minecraft.screen != null || !minecraft.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        return minecraft.isWindowActive();
    }
}
