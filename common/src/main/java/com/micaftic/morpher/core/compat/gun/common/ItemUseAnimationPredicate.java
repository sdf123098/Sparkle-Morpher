package com.micaftic.morpher.core.compat.gun.common;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.micaftic.morpher.core.compat.gun.swarfare.SWarfareCompat;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;

import java.util.Objects;

public class ItemUseAnimationPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<LivingAnimatable<?>> event) {
        LivingEntity livingEntity = (LivingEntity) ((LivingAnimatable) event.getAnimatable()).getEntity();
        if (livingEntity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return HostPlayState.Decision.STOP;
        }
        ItemStack itemInHand = livingEntity.getItemInHand(InteractionHand.MAIN_HAND);
        HostPlayState.Decision taczPlayState = TacCompat.handleGunActionAnimState(itemInHand, event);
        if (taczPlayState != null) {
            return taczPlayState;
        }
        if (!livingEntity.swinging && !livingEntity.isUsingItem()) {
            HostPlayState.Decision playState = SWarfareCompat.handleGunActionAnim(itemInHand, event);
            return Objects.requireNonNullElse(playState, HostPlayState.Decision.STOP);
        }
        return HostPlayState.Decision.STOP;
    }

    public static boolean isLoaded() {
        return TacCompat.isLoaded() || SWarfareCompat.isLoaded();
    }
}
