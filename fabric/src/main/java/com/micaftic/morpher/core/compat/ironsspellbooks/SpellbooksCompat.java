package com.micaftic.morpher.core.compat.ironsspellbooks;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class SpellbooksCompat {

    private SpellbooksCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.ironsspellbooks.fabric.SpellbooksCompatImpl.isLoaded();
    }

    public static void registerBindings(HostMolangSink binding) {
        com.micaftic.morpher.core.compat.ironsspellbooks.fabric.SpellbooksCompatImpl.registerBindings(binding);
    }

    public static @Nullable HostPlayState.Decision resolvePlayState(HostActionEvent<LivingAnimatable<?>> event, LivingEntity entity) {
        return com.micaftic.morpher.core.compat.ironsspellbooks.fabric.SpellbooksCompatImpl.resolvePlayState(event, entity);
    }
}
