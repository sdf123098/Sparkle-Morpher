package com.micaftic.morpher.core.compat.slashblade;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class SlashBladeCompat {

    private SlashBladeCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isSlashBladeItem(ItemStack itemStack) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static String getComboAnimName(AnimationEvent<? extends LivingAnimatable<?>> event) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static PlayState handleSlashBladeAnim(LivingEntity livingEntity, AnimationEvent<? extends LivingAnimatable<?>> event, String str, ILoopType loopType) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerControllerFunctions(CtrlBinding ctrlBinding) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean hasNewApi() {
        throw new AssertionError();
    }
}
