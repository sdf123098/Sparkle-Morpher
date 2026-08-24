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

    public static boolean isLoaded() { return false;
    }

    public static boolean isSlashBladeItem(ItemStack itemStack) { return false;
    }

    public static String getComboAnimName(AnimationEvent<? extends LivingAnimatable<?>> event) {
        return null;
    }

    public static PlayState handleSlashBladeAnim(LivingEntity livingEntity, AnimationEvent<? extends LivingAnimatable<?>> event, String str, ILoopType loopType) {
        return null;
    }

    public static void registerControllerFunctions(CtrlBinding ctrlBinding) {
    }

    public static boolean hasNewApi() { return false;
    }
}
