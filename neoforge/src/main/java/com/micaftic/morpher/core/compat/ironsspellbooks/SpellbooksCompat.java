package com.micaftic.morpher.core.compat.ironsspellbooks;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class SpellbooksCompat {

    private SpellbooksCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static void registerBindings(HostMolangSink binding) {
        binding.var("iss_animation", event -> MolangValue.text(""));
    }

    public static @Nullable HostPlayState.Decision resolvePlayState(HostActionEvent<LivingAnimatable<?>> event, LivingEntity entity) {
        return null;
    }
}
