package com.micaftic.morpher.core.compat.gun.common;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.micaftic.morpher.core.compat.gun.swarfare.SWarfareCompat;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;

import java.util.Objects;

public class ItemUseAnimationPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public PlayState predicate(AnimationEvent<LivingAnimatable<?>> event, ExpressionEvaluator<?> evaluator) {
        LivingEntity livingEntity = (LivingEntity) ((LivingAnimatable) event.getAnimatable()).getEntity();
        if (livingEntity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return PlayState.STOP;
        }
        if (!livingEntity.swinging && !livingEntity.isUsingItem()) {
            ItemStack itemInHand = livingEntity.getItemInHand(InteractionHand.MAIN_HAND);
            PlayState playState = TacCompat.handleGunActionAnimState(itemInHand, event);
            if (playState == null) {
                playState = SWarfareCompat.handleGunActionAnim(itemInHand, event);
            }
            return Objects.requireNonNullElse(playState, PlayState.STOP);
        }
        return PlayState.STOP;
    }

    public static boolean isLoaded() {
        return TacCompat.isLoaded() || SWarfareCompat.isLoaded();
    }
}
