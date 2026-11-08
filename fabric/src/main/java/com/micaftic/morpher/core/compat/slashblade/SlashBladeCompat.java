package com.micaftic.morpher.core.compat.slashblade;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class SlashBladeCompat {

    private SlashBladeCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.isLoaded();
    }

    public static boolean isSlashBladeItem(ItemStack itemStack) {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.isSlashBladeItem(itemStack);
    }

    public static String getComboAnimName(AnimationEvent<? extends LivingAnimatable<?>> event) {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.getComboAnimName(event);
    }

    public static PlayState handleSlashBladeAnim(LivingEntity livingEntity, AnimationEvent<? extends LivingAnimatable<?>> event, String str, ILoopType loopType) {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.handleSlashBladeAnim(livingEntity, event, str, loopType);
    }

    public static void registerControllerFunctions(CtrlBinding ctrlBinding) {
        com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.registerControllerFunctions(ctrlBinding);
    }

    public static boolean hasNewApi() {
        return com.micaftic.morpher.core.compat.slashblade.fabric.SlashBladeCompatImpl.hasNewApi();
    }
}
