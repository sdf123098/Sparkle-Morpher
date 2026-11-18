package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionHold;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.entity.LivingEntityFrameState;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.model.ModelActionProfile;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;

public class OffHandHoldPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<LivingAnimatable<?>> event) {
        LivingEntity entity = event.getAnimatable().getEntity();
        if (entity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return HostPlayState.Decision.STOP;
        }
        ModelAssembly modelAssembly = event.getAnimatable().getModelAssembly();
        PlayerModelBundle animationBundle = modelAssembly == null ? null : modelAssembly.getAnimationBundle();
        if (animationBundle == null || animationBundle.getActionProfile() == ModelActionProfile.VANILLA_HUMANOID) {
            return HostPlayState.Decision.STOP;
        }
        ItemStack itemInHand = entity.getItemInHand(InteractionHand.OFF_HAND);
        LivingEntityFrameState<?> frameState = event.getAnimatable().getPositionTracker();
        if (!isSameItem(itemInHand, frameState, InteractionHand.OFF_HAND)) {
            frameState.setHandItemsForAnimation(itemInHand, InteractionHand.OFF_HAND);
            event.getController().stopTransition();
        }
        if (!checkSwingAndUse(entity, InteractionHand.OFF_HAND)) return HostPlayState.Decision.PAUSE;
        int i = event.getAnimatable().getModelAssembly().getModelData().getFormatVersion();
        if (itemInHand.is(Items.CROSSBOW) && CrossbowItem.isCharged(itemInHand)) {
            return IAnimationPredicate.playAnimationWithValid(event, "hold_offhand:charged_crossbow", CodedLoopMode.LOOP, i);
        }
        ConditionHold conditionHold = event.getAnimatable().getModelConfig().getHoldOffhand();
        if (conditionHold != null) {
            String str = conditionHold.doTest(entity, InteractionHand.OFF_HAND);
            if (StringUtils.isNoneBlank(str)) {
                if ("hold_offhand:lance".equals(str)) {
                    return IAnimationPredicate.playAnimationWithValid(event, str, CodedLoopMode.HOLD_ON_LAST_FRAME, i);
                }
                return IAnimationPredicate.playAnimationWithValid(event, str, CodedLoopMode.LOOP, i);
            }
        }
        return HostPlayState.Decision.STOP;
    }

    private boolean isSameItem(ItemStack stack, LivingEntityFrameState<?> frameState, InteractionHand hand) {
        ItemStack preItem = frameState.getHandItemsForAnimation(hand);
        if (preItem.isDamaged()) {
            return ItemStack.isSameItem(stack, preItem);
        }
        return ItemStack.matches(stack, preItem);
    }

    private boolean checkSwingAndUse(LivingEntity entity, InteractionHand hand) {
        if (InputStateKey.isSwinging(entity, hand)) {
            return false;
        }
        return !entity.isUsingItem() || entity.getUsedItemHand() != hand;
    }
}
