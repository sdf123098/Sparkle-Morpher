package com.micaftic.morpher.core.compat.slashblade;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class SlashBladeCompat {

    private SlashBladeCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.isLoaded();
    }

    public static boolean isSlashBladeItem(ItemStack itemStack) {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.isSlashBladeItem(itemStack);
    }

    public static String getComboAnimName(HostActionEvent<? extends LivingAnimatable<?>> event) {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.getComboAnimName(event);
    }

    public static @Nullable HostPlayState.Decision handleSlashBladeAnim(
            LivingEntity livingEntity,
            HostActionEvent<? extends LivingAnimatable<?>> event,
            String animation,
            CodedLoopMode loopType) {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.handleSlashBladeAnim(livingEntity, event, animation, loopType);
    }

    public static void registerControllerFunctions(HostMolangSink sink) {
        com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.registerControllerFunctions(sink);
    }

    public static boolean hasNewApi() {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.hasNewApi();
    }
}
