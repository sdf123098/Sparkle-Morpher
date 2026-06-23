package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class KeyMappingFactory {
    private KeyMappingFactory() {}
    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, String category) { return new KeyMapping(name, type, keyCode, category); }
    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, String category) { return new KeyMapping(name, type, keyCode, category); }
    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode) { return keyMapping.matches(keyCode, scanCode); }
}