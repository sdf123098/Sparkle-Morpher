package com.micaftic.morpher.core.compat.optifine;

import dev.architectury.injectables.annotations.ExpectPlatform;

public final class OptiFineDetector {

    private OptiFineDetector() {
    }

    @ExpectPlatform
    public static boolean isOptifinePresent() {
        throw new AssertionError();
    }
}
