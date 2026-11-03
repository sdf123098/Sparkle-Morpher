package com.micaftic.morpher.core.compat.slashblade;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Loader-neutral facade. The gate lives in {@link SlashBladeModState} and all
 * SlashBlade class references live in {@link SlashBladeBridge}; neither is
 * classloaded with SlashBlade absent.
 */
public final class SlashBladeCompat {

    private SlashBladeCompat() {
    }

    public static boolean isLoaded() {
        return SlashBladeModState.LOADED;
    }

    public static boolean isSlashBladeItem(ItemStack itemStack) {
        return SlashBladeModState.LOADED && SlashBladeBridge.isSlashBladeItem(itemStack);
    }

    public static String getComboAnimName(AnimationEvent<? extends LivingAnimatable<?>> event) {
        if (!SlashBladeModState.LOADED || event == null || event.getAnimatable() == null) {
            return "";
        }
        return SlashBladeBridge.getComboAnimationName(event.getAnimatable().getEntity());
    }

    @Nullable
    public static PlayState handleSlashBladeAnim(LivingEntity livingEntity, AnimationEvent<? extends LivingAnimatable<?>> event, String str, ILoopType loopType) {
        if (!SlashBladeModState.LOADED) {
            return null;
        }
        return SlashBladeBridge.handleSlashBladeAnim(livingEntity, event, str, loopType);
    }

    public static void registerControllerFunctions(CtrlBinding ctrlBinding) {
        if (SlashBladeModState.LOADED) {
            SlashBladeBridge.registerBindings(ctrlBinding);
        } else {
            ctrlBinding.livingEntityVar("slashblade_animation", it -> StringPool.EMPTY);
        }
    }

    public static boolean hasNewApi() {
        return SlashBladeModState.LOADED;
    }
}
