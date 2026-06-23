package com.micaftic.morpher.core.compat.immersivemelodies;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import dev.architectury.injectables.annotations.ExpectPlatform;
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

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void updateMelodyProgress(LivingEntity livingEntity, ImmersiveMelodiesData imData) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerBindings(CtrlBinding binding) {
        throw new AssertionError();
    }
}
