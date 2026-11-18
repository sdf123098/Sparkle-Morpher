package com.micaftic.morpher.core.compat.immersivemelodies.fabric;

import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.core.compat.immersivemelodies.ImmersiveMelodiesCompat;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.LivingEntity;

public final class ImmersiveMelodiesCompatImpl {

    private ImmersiveMelodiesCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static void updateMelodyProgress(LivingEntity livingEntity, ImmersiveMelodiesCompat.ImmersiveMelodiesData imData) {
    }

    public static void registerBindings(HostMolangSink binding) {
        binding.var("im_pitch", event -> MolangValue.number(0.0));
        binding.var("im_volume", event -> MolangValue.number(0.0));
        binding.var("im_current", event -> MolangValue.number(0.0));
        binding.var("im_delta", event -> MolangValue.number(0.0));
        binding.var("im_time", event -> MolangValue.number(0.0));
    }
}
