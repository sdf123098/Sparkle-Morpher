package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.animation.condition.ConditionHold;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingEntityFrameState;
import com.micaftic.morpher.client.model.ModelActionProfile;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;

public class OffHandHoldPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public PlayState predicate(AnimationEvent<LivingAnimatable<?>> event, ExpressionEvaluator<?> evaluator) {
        LivingEntity entity = event.getAnimatable().getEntity();
        if (entity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return PlayState.STOP;
        }
        if (event.getAnimatable().getModelAssembly().getAnimationBundle().getActionProfile() == ModelActionProfile.VANILLA_HUMANOID) {
            return PlayState.STOP;
        }
        ItemStack itemInHand = entity.getItemInHand(InteractionHand.OFF_HAND);
        LivingEntityFrameState<?> frameState = ((LivingAnimatable) event.getAnimatable()).getPositionTracker();
        if (!isSameItem(itemInHand, frameState, InteractionHand.OFF_HAND)) {
            frameState.setHandItemsForAnimation(itemInHand, InteractionHand.OFF_HAND);
            event.getController().stopTransition();
        }
        if (!checkSwingAndUse(entity, InteractionHand.OFF_HAND)) return PlayState.PAUSE;
        int i = event.getAnimatable().getModelAssembly().getModelData().getFormatVersion();
        if (itemInHand.is(Items.CROSSBOW) && CrossbowItem.isCharged(itemInHand)) {
            return IAnimationPredicate.playAnimationWithValid(event, "hold_offhand:charged_crossbow", ILoopType.EDefaultLoopTypes.LOOP, i);
        }
        ConditionHold conditionHold = event.getAnimatable().getModelConfig().getHoldOffhand();
        if (conditionHold != null) {
            String str = conditionHold.doTest(entity, InteractionHand.OFF_HAND);
            if (StringUtils.isNoneBlank(str)) {
                return IAnimationPredicate.playAnimationWithValid(event, str, ILoopType.EDefaultLoopTypes.LOOP, i);
            }
        }
        return PlayState.STOP;
    }

    private boolean isSameItem(ItemStack stack, LivingEntityFrameState<?> frameState, InteractionHand hand) {
        ItemStack preItem = frameState.getHandItemsForAnimation(hand);
        if (preItem.isDamaged()) {
            return ItemStack.isSameItem(stack, preItem);
        }
        return ItemStack.matches(stack, preItem);
    }

    private boolean checkSwingAndUse(LivingEntity entity, InteractionHand hand) {
        if (entity.swinging && entity.swingingArm == hand) {
            return false;
        }
        return !entity.isUsingItem() || entity.getUsedItemHand() != hand;
    }
}
