package com.micaftic.morpher.core.compat.touhoulittlemaid;

import net.minecraft.world.entity.Entity;

public final class TouhouMaidCompat {

    private TouhouMaidCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouMaidCompatImpl.isLoaded();
    }

    public static void init() {
        com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouMaidCompatImpl.init();
    }

    public static boolean isMaidEntity(Entity entity) {
        return com.micaftic.morpher.core.compat.touhoulittlemaid.fabric.TouhouMaidCompatImpl.isMaidEntity(entity);
    }




}
