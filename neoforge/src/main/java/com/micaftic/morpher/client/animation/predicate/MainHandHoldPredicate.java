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
import com.micaftic.morpher.core.api.item.ToolActionBridge;
import com.micaftic.morpher.core.compat.gun.swarfare.SWarfareCompat;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;

public class MainHandHoldPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
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
        ItemStack mainHandItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
        LivingEntityFrameState<?> frameState = event.getAnimatable().getPositionTracker();
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
        if (!checkSwingAndUse(event, entity, InteractionHand.MAIN_HAND)) {
            return HostPlayState.Decision.PAUSE;
        }
        HostPlayState.Decision playState = TacCompat.handleGunHoldAnimState(mainHandItem, event);
        if (playState != null) {
            return playState;
        }
        HostPlayState.Decision gunPlayState = SWarfareCompat.handleGunHoldAnim(mainHandItem, event);
        if (gunPlayState != null) {
            return gunPlayState;
        }
        if (mainHandItem.is(Items.CROSSBOW) && CrossbowItem.isCharged(mainHandItem)) {
            return IAnimationPredicate.playContinuousAnimation(event, "hold_mainhand:charged_crossbow");
        }
        boolean flag = TouhouLittleMaidCompat.isMaidSitting(entity);
        if (isFishing || flag) {
            return IAnimationPredicate.playContinuousAnimation(event, "hold_mainhand:fishing");
        }
        ConditionHold conditionHold = event.getAnimatable().getModelConfig().getHoldMainhand();
        if (conditionHold != null) {
            String str = conditionHold.doTest(entity, InteractionHand.MAIN_HAND);
            if (StringUtils.isNoneBlank(str)) {
                CodedLoopMode fallback = "hold_mainhand:lance".equals(str)
                        ? CodedLoopMode.HOLD_ON_LAST_FRAME
                        : CodedLoopMode.LOOP;
                return IAnimationPredicate.playContinuousAnimation(event, str, fallback);
            }
        }
        return HostPlayState.Decision.STOP;
    }

    private boolean isSameItem(ItemStack itemStack, LivingEntityFrameState<?> frameState, InteractionHand hand) {
        ItemStack preItem = frameState.getHandItemsForAnimation(hand);
        if (preItem.isDamaged()) {
            return ItemStack.isSameItem(itemStack, preItem);
        }
        return ItemStack.matches(itemStack, preItem);
    }

    private boolean checkSwingAndUse(HostActionEvent<LivingAnimatable<?>> event, LivingEntity entity, InteractionHand hand) {
        if (shouldPauseForSwing(event, entity, hand)) {
            return false;
        }
        return !InputStateKey.isUsingItem(entity, hand);
    }

    private static boolean shouldPauseForSwing(HostActionEvent<LivingAnimatable<?>> event, LivingEntity entity, InteractionHand hand) {
        if (!InputStateKey.isSwinging(entity, hand) && !(entity instanceof Player && InputStateKey.isLocalSwinging(hand))) {
            return false;
        }
        if (InputStateKey.isVanillaSwinging(entity, hand) || InputStateKey.getLocalSwingPulseAge() <= 1) {
            return true;
        }
        for (var slot : event.getAnimatable().getControllerSlots()) {
            if ("player.swing".equals(slot.getName())) {
                return slot.isPlaying();
            }
        }
        return false;
    }
}
