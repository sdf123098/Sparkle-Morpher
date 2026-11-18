package com.micaftic.morpher.core.compat.immersivemelodies;

import com.micaftic.morpher.client.core.HostMolangSink;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.LivingEntity;

public final class ImmersiveMelodiesCompat {

    public static final class ImmersiveMelodiesData {
        public float pitch = 0f;
        public float volume = 0f;
        public float current = 0f;
        public long delta = 0L;
        public long time = 0L;
    }

    private ImmersiveMelodiesCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static void updateMelodyProgress(LivingEntity livingEntity, ImmersiveMelodiesData imData) {
    }

    public static void registerBindings(HostMolangSink binding) {
        binding.var("im_pitch", event -> MolangValue.number(0.0));
        binding.var("im_volume", event -> MolangValue.number(0.0));
        binding.var("im_current", event -> MolangValue.number(0.0));
        binding.var("im_delta", event -> MolangValue.number(0.0));
        binding.var("im_time", event -> MolangValue.number(0.0));
    }
}
