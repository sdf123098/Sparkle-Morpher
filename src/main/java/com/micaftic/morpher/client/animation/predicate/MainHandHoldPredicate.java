package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.core.compat.gun.swarfare.SWarfareCompat;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.animation.condition.ConditionHold;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingEntityFrameState;
import com.micaftic.morpher.client.model.ModelActionProfile;
import com.micaftic.morpher.core.api.item.ToolActionBridge;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;

public class MainHandHoldPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public PlayState predicate(AnimationEvent<LivingAnimatable<?>> event, ExpressionEvaluator<?> evaluator) {
        LivingEntity entity = event.getAnimatable().getEntity();
        if (entity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return PlayState.STOP;
        }
        if (event.getAnimatable().getModelAssembly().getAnimationBundle().getActionProfile() == ModelActionProfile.VANILLA_HUMANOID) {
            return PlayState.STOP;
        }
        ItemStack mainHandItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
        LivingEntityFrameState<?> frameState = ((LivingAnimatable) event.getAnimatable()).getPositionTracker();
        boolean isFishing = entity instanceof Player player
                && player.fishing != null
                && !player.fishing.isRemoved()
                && ToolActionBridge.canFishingRodCast(mainHandItem);
        if (frameState.updateFishingForAnimation(isFishing)) {
            event.getController().stopTransition();
        }
        if (!isSameItem(mainHandItem, frameState, InteractionHand.MAIN_HAND)) {
            frameState.setHandItemsForAnimation(mainHandItem, InteractionHand.MAIN_HAND);
            event.getController().stopTransition();
        }
        if (!checkSwingAndUse(entity, InteractionHand.MAIN_HAND)) return PlayState.PAUSE;
        int i = event.getAnimatable().getModelAssembly().getModelData().getFormatVersion();
        PlayState playState = TacCompat.handleGunHoldAnimState(mainHandItem, event);
        if (playState != null) {
            return playState;
        }
        PlayState gunPlayState = SWarfareCompat.handleGunHoldAnim(mainHandItem, event);
        if (gunPlayState != null) {
            return gunPlayState;
        }
        if (mainHandItem.is(Items.CROSSBOW) && CrossbowItem.isCharged(mainHandItem)) {
            return IAnimationPredicate.playAnimationWithValid(event, "hold_mainhand:charged_crossbow", ILoopType.EDefaultLoopTypes.LOOP, i);
        }
        boolean flag = TouhouLittleMaidCompat.isMaidSitting(entity);
        if (isFishing || flag) {
            return IAnimationPredicate.playAnimationWithValid(event, "hold_mainhand:fishing", ILoopType.EDefaultLoopTypes.LOOP, i);
        }
        ConditionHold conditionHold = event.getAnimatable().getModelConfig().getHoldMainhand();
        if (conditionHold != null) {
            String str = conditionHold.doTest(entity, InteractionHand.MAIN_HAND);
            if (StringUtils.isNoneBlank(str)) {
                return IAnimationPredicate.playAnimationWithValid(event, str, ILoopType.EDefaultLoopTypes.LOOP, i);
            }
        }
        return PlayState.STOP;
    }

    private boolean isSameItem(ItemStack itemStack, LivingEntityFrameState<?> frameState, InteractionHand hand) {
        ItemStack preItem = frameState.getHandItemsForAnimation(hand);
        if (preItem.isDamaged()) {
            return ItemStack.isSameItem(itemStack, preItem);
        }
        return ItemStack.matches(itemStack, preItem);
    }

    private boolean checkSwingAndUse(LivingEntity entity, InteractionHand hand) {
        if (entity.swinging && entity.swingingArm == hand) {
            return false;
        }
        return !entity.isUsingItem() || entity.getUsedItemHand() != hand;
    }
}
