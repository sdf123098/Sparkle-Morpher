package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class KeyMappingFactory {
    private KeyMappingFactory() {}
    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, String category) { return new KeyMapping(name, type, keyCode, category); }
    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, String category) { return new KeyMapping(name, type, keyCode, category); }
    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode) { return isActiveAndMatches(keyMapping, keyCode, scanCode, 0); }
    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode, int modifiers) { return keyMapping.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode)); }
    public static boolean isMouseActiveAndMatches(KeyMapping keyMapping, int button) { return keyMapping.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(button)); }
}
