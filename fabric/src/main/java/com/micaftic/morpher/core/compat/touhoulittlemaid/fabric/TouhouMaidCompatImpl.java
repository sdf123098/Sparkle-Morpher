package com.micaftic.morpher.core.compat.touhoulittlemaid.fabric;

import net.minecraft.world.entity.Entity;

import java.util.Map;

public final class TouhouMaidCompatImpl {

    private TouhouMaidCompatImpl() {
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
