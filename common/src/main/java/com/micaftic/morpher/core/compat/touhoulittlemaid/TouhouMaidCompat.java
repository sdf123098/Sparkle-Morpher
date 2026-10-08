package com.micaftic.morpher.core.compat.touhoulittlemaid;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.Entity;

public final class TouhouMaidCompat {

    private TouhouMaidCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void init() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isMaidEntity(Entity entity) {
        throw new AssertionError();
    }

}
