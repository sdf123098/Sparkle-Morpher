package com.micaftic.morpher.core.compat.immersivemelodies;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
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
        return com.micaftic.morpher.core.compat.immersivemelodies.fabric.ImmersiveMelodiesCompatImpl.isLoaded();
    }

    public static void updateMelodyProgress(LivingEntity livingEntity, ImmersiveMelodiesData imData) {
        com.micaftic.morpher.core.compat.immersivemelodies.fabric.ImmersiveMelodiesCompatImpl.updateMelodyProgress(livingEntity, imData);
    }

    public static void registerBindings(CtrlBinding binding) {
        com.micaftic.morpher.core.compat.immersivemelodies.fabric.ImmersiveMelodiesCompatImpl.registerBindings(binding);
    }
}
