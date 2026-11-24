package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.SpmPlayerEntity;
import com.micaftic.morpher.client.input.InputStateKey;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;

public class FirstPersonWeaponAnimationPredicate implements IAnimationPredicate<SpmPlayerEntity> {

    private static final int USE_START_MARKER = 203;
    private static final int SWING_START_MARKER = 202;

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<SpmPlayerEntity> event) {
        SpmPlayerEntity animatable = event.getAnimatable();
        LocalPlayer player = animatable.getEntity();
        if (player == null || animatable instanceof IPreviewAnimatable) {
            return HostPlayState.Decision.STOP;
        }
        if (!player.isSleeping() && InputStateKey.isAnyHandSwinging(player)) {
            InteractionHand swingingHand = InputStateKey.getSwingingHand(player);
            boolean swingStart = InputStateKey.getSwingTicks(player, event.getPartialTick()) < 1.0f
                    && animatable.getPositionTracker().markProcessed(SWING_START_MARKER);
            if (swingStart) {
                event.getController().stopTransition();
                String swing = selectSwingAnimation(animatable, swingingHand);
                if (swing != null) {
                    return IAnimationPredicate.playAnimationWithLoop(event, swing, CodedLoopMode.PLAY_ONCE);
                }
            }
            return HostPlayState.Decision.CONTINUE;
        }
        InteractionHand usedHand = InputStateKey.getUsedItemHand(player);
        if (InputStateKey.isUsingItem(player, usedHand) && !player.isSleeping()) {
            if (InputStateKey.getTicksUsingItem(player) == 1
                    && animatable.getPositionTracker().markProcessed(USE_START_MARKER)) {
                event.getController().stopTransition();
            }
            String use = selectUseAnimation(animatable, usedHand);
            if (use != null) {
                return IAnimationPredicate.playContinuousAnimation(event, use);
            }
        }
        String hold = selectHoldAnimation(animatable, InteractionHand.MAIN_HAND);
        if (hold != null) {
            return IAnimationPredicate.playContinuousAnimation(event, hold);
        }
        String offhandHold = selectHoldAnimation(animatable, InteractionHand.OFF_HAND);
        if (offhandHold != null) {
            return IAnimationPredicate.playContinuousAnimation(event, offhandHold);
        }
        return HostPlayState.Decision.STOP;
    }

    private String selectHoldAnimation(SpmPlayerEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getEntity().getItemInHand(hand);
        if (stack.isEmpty()) {
            return null;
        }
        String prefix = hand == InteractionHand.MAIN_HAND ? "hold_mainhand" : "hold_offhand";
        String itemType = InnerClassify.getItemType(stack);
        if (StringUtils.isNoneBlank(itemType)) {
            String classified = prefix + ":" + itemType;
            if (hasAnimation(entity, classified)) {
                return classified;
            }
            if ("spear".equals(itemType) && hasAnimation(entity, prefix + ":lance")) {
                return prefix + ":lance";
            }
        }
        return hasAnimation(entity, prefix) ? prefix : null;
    }

    private String selectUseAnimation(SpmPlayerEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getEntity().getItemInHand(hand);
        if (stack.isEmpty()) {
            return null;
        }
        String prefix = hand == InteractionHand.MAIN_HAND ? "use_mainhand" : "use_offhand";
        String useName = stack.getUseAnimation().name().toLowerCase(java.util.Locale.US);
        if (StringUtils.isNoneBlank(useName)) {
            String classified = prefix + ":" + useName;
            if (hasAnimation(entity, classified)) {
                return classified;
            }
            if ("spear".equals(useName) && hasAnimation(entity, prefix + ":lance")) {
                return prefix + ":lance";
            }
            if ("trident".equals(useName) && hasAnimation(entity, prefix + ":spear")) {
                return prefix + ":spear";
            }
        }
        return hasAnimation(entity, prefix) ? prefix : null;
    }

    private String selectSwingAnimation(SpmPlayerEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getEntity().getItemInHand(hand);
        String itemType = stack.isEmpty() ? null : InnerClassify.getItemType(stack);
        if (StringUtils.isNoneBlank(itemType)) {
            String classified = "swing:" + itemType;
            if (hasAnimation(entity, classified)) {
                return classified;
            }
            if ("spear".equals(itemType) && hasAnimation(entity, "swing:lance")) {
                return "swing:lance";
            }
        }
        String handFallback = hand == InteractionHand.MAIN_HAND ? "swing_hand" : "swing_offhand";
        if (hasAnimation(entity, handFallback)) {
            return handFallback;
        }
        if (hand == InteractionHand.MAIN_HAND && hasAnimation(entity, "attack_empty")) {
            return "attack_empty";
        }
        return null;
    }

    private static boolean hasAnimation(SpmPlayerEntity entity, String animationName) {
        if (StringUtils.isBlank(animationName)) {
            return false;
        }
        var animation = entity.getAnimation(animationName);
        return animation != null && !animation.isEmpty();
    }
}
