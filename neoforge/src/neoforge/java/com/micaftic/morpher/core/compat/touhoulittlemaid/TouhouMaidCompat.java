package com.micaftic.morpher.core.compat.touhoulittlemaid;

import net.minecraft.world.entity.Entity;

import java.util.Map;

public final class TouhouMaidCompat {

    private TouhouMaidCompat() {
    }

    public static boolean isLoaded() {
        return TouhouLittleMaidAccess.isLoaded();
    }

    public static void init() {
    }

    public static boolean isMaidEntity(Entity entity) {
        return TouhouLittleMaidAccess.isMaid(entity);
    }



}
