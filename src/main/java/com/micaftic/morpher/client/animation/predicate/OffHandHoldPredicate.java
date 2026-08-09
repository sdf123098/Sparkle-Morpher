package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.animation.condition.ConditionHold;
import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingEntityFrameState;
import com.micaftic.morpher.client.model.ModelActionProfile;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;
import com.micaftic.morpher.core.api.item.WeaponKind;

public class OffHandHoldPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public PlayState predicate(AnimationEvent<LivingAnimatable<?>> event, ExpressionEvaluator<?> evaluator) {
        LivingEntity entity = event.getAnimatable().getEntity();
        if (entity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return PlayState.STOP;
        }
        ModelAssembly modelAssembly = event.getAnimatable().getModelAssembly();
        PlayerModelBundle animationBundle = modelAssembly == null ? null : modelAssembly.getAnimationBundle();
        if (animationBundle == null || animationBundle.getActionProfile() == ModelActionProfile.VANILLA_HUMANOID) {
            return PlayState.STOP;
        }
        ItemStack itemInHand = entity.getItemInHand(InteractionHand.OFF_HAND);
        LivingEntityFrameState<?> c0675x43c72e02Mo1215x3cfc56ba = ((LivingAnimatable) event.getAnimatable()).getPositionTracker();
        if (!isSameItem(itemInHand, c0675x43c72e02Mo1215x3cfc56ba, InteractionHand.OFF_HAND)) {
            c0675x43c72e02Mo1215x3cfc56ba.setHandItemsForAnimation(itemInHand, InteractionHand.OFF_HAND);
            event.getController().stopTransition();
        }
        if (!checkSwingAndUse(entity, InteractionHand.OFF_HAND)) {
            return PlayState.PAUSE;
        }
        int i = event.getAnimatable().getModelAssembly().getModelData().getFormatVersion();
        if (isBinaryYsmSpear(event, itemInHand)) {
            return holdBinaryYsmSpear(event, "hold_offhand:spear");
        }
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

    private boolean isBinaryYsmSpear(AnimationEvent<LivingAnimatable<?>> event, ItemStack stack) {
        // Folder models use the synthetic format 65535.  Old binary .ysm files
        // can store a one-shot switch pose as hold_*:spear, which must not loop.
        return event.getAnimatable().getModelAssembly().getModelData().getFormatVersion() != 65535
                && InnerClassify.getWeaponKind(stack) == WeaponKind.SPEAR;
    }

    private PlayState holdBinaryYsmSpear(AnimationEvent<LivingAnimatable<?>> event, String animationName) {
        if (event.getAnimatable().getAnimation(animationName) == null) {
            return PlayState.STOP;
        }
        return IAnimationPredicate.playAnimationWithLoop(event, animationName, ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME);
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
        return !InputStateKey.isUsingItem(entity, hand);
    }
}
