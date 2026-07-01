package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.KeyMapping;

public final class KeyMappingFactory {

    private KeyMappingFactory() {
    }

    @ExpectPlatform
    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, String category) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, String category) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode, int modifiers) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isMouseActiveAndMatches(KeyMapping keyMapping, int button) {
        throw new AssertionError();
    }
}
