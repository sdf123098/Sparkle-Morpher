package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class KeyMappingFactory {

    private KeyMappingFactory() {
    }

    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, String category) {
        return com.micaftic.morpher.core.api.client.fabric.KeyMappingFactoryImpl.createInGameAlt(name, type, keyCode, category);
    }

    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, String category) {
        return com.micaftic.morpher.core.api.client.fabric.KeyMappingFactoryImpl.createInGameNone(name, type, keyCode, category);
    }

    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode) {
        return com.micaftic.morpher.core.api.client.fabric.KeyMappingFactoryImpl.isActiveAndMatches(keyMapping, keyCode, scanCode);
    }

    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode, int modifiers) {
        return com.micaftic.morpher.core.api.client.fabric.KeyMappingFactoryImpl.isActiveAndMatches(keyMapping, keyCode, scanCode, modifiers);
    }

    public static boolean isMouseActiveAndMatches(KeyMapping keyMapping, int button) {
        return com.micaftic.morpher.core.api.client.fabric.KeyMappingFactoryImpl.isMouseActiveAndMatches(keyMapping, button);
    }
}
