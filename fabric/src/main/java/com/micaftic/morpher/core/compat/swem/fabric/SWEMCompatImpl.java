package com.micaftic.morpher.core.compat.swem.fabric;

import com.micaftic.morpher.client.core.HostMolangSink;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.LivingEntity;

public final class SWEMCompatImpl {

    private SWEMCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static String getHorseGaitName(LivingEntity livingEntity) {
        return "";
    }

    public static void registerControllerFunctions(HostMolangSink sink) {
        sink.var("swem_is_ride", event -> MolangValue.bool(false));
        sink.var("swem_state", event -> MolangValue.text(""));
    }
}
