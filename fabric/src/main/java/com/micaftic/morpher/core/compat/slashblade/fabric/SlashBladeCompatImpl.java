package com.micaftic.morpher.core.compat.slashblade.fabric;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class SlashBladeCompatImpl {

    private SlashBladeCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isSlashBladeItem(ItemStack itemStack) {
        return false;
    }

    public static String getComboAnimName(HostActionEvent<? extends LivingAnimatable<?>> event) {
        return "";
    }

    public static @Nullable HostPlayState.Decision handleSlashBladeAnim(
            LivingEntity livingEntity,
            HostActionEvent<? extends LivingAnimatable<?>> event,
            String animation,
            CodedLoopMode loopType) {
        return null;
    }

    public static void registerControllerFunctions(HostMolangSink sink) {
        sink.var("slashblade_animation", event -> MolangValue.text(""));
    }

    public static boolean hasNewApi() {
        return false;
    }
}
