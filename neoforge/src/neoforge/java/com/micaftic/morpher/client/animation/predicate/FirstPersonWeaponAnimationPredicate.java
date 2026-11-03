package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.PlayerGeoEntity;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;

/**
 * bbmodel / figura 导入模型（{@code VANILLA_HUMANOID}）第一人称手臂的自动武器兜底谓词。
 *
 * <p>YSM 模型的第一人称手由 molang（{@code fp.arm_ctrl_*}）或模型自带 fp.arm 动画驱动；
 * bbmodel/figura 没有作者化的 fp.arm 内容时手部会完全不动。本谓词为它们按动作从
 * arm 动画表（{@link PlayerGeoEntity#getAnimation}）自动挑选兜底动画：
 * 优先命中物品分类的具体 key（如 {@code hold_mainhand:sword} / {@code use_mainhand:bow} /
 * {@code swing:axe}，模型自带动画），找不到则回退预设的通用 key
 * （{@code hold_mainhand} / {@code use_mainhand} / {@code swing_hand} / {@code attack_empty}）。
 * 空手时返回 STOP，不干扰其余 fp.arm 槽位。</p>
 *
 * <p>只应在 bbmodel/figura 模型的 {@code fp.arm.weapon} 控制器上使用
 * （见 {@code FirstPersonArmAnimationController}），YSM 模型不会安装该槽位。</p>
 */
public class FirstPersonWeaponAnimationPredicate implements IAnimationPredicate<PlayerGeoEntity> {

    private static final int USE_START_MARKER = 203;
    private static final int SWING_START_MARKER = 202;

    @Override
    public PlayState predicate(AnimationEvent<PlayerGeoEntity> event, ExpressionEvaluator<?> evaluator) {
        PlayerGeoEntity animatable = event.getAnimatable();
        LocalPlayer player = animatable.getEntity();
        if (player == null || animatable instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        if (!player.isSleeping() && InputStateKey.isAnyHandSwinging(player)) {
            InteractionHand swingingHand = InputStateKey.getSwingingHand(player);
            boolean swingStart = player.swingTime == 0
                    && animatable.getPositionTracker().markProcessed(SWING_START_MARKER);
            if (swingStart) {
                event.getController().stopTransition();
                String swing = selectSwingAnimation(animatable, swingingHand);
                if (swing != null) {
                    return IAnimationPredicate.playAnimationWithLoop(event, swing, ILoopType.EDefaultLoopTypes.PLAY_ONCE);
                }
            }
            return PlayState.CONTINUE;
        }
        InteractionHand usedHand = InputStateKey.getUsedItemHand(player);
        if (InputStateKey.isUsingItem(player, usedHand) && !player.isSleeping()) {
            if (InputStateKey.getTicksUsingItem(player) == 1
                    && animatable.getPositionTracker().markProcessed(USE_START_MARKER)) {
                event.getController().stopTransition();
            }
            String use = selectUseAnimation(animatable, usedHand);
            if (use != null) {
                return IAnimationPredicate.playAnimationWithLoop(event, use, ILoopType.EDefaultLoopTypes.LOOP);
            }
        }
        String hold = selectHoldAnimation(animatable, InteractionHand.MAIN_HAND);
        if (hold != null) {
            return IAnimationPredicate.playAnimationWithLoop(event, hold, ILoopType.EDefaultLoopTypes.LOOP);
        }
        String offhandHold = selectHoldAnimation(animatable, InteractionHand.OFF_HAND);
        if (offhandHold != null) {
            return IAnimationPredicate.playAnimationWithLoop(event, offhandHold, ILoopType.EDefaultLoopTypes.LOOP);
        }
        return PlayState.STOP;
    }

    private String selectHoldAnimation(PlayerGeoEntity entity, InteractionHand hand) {
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
        }
        return hasAnimation(entity, prefix) ? prefix : null;
    }

    private String selectUseAnimation(PlayerGeoEntity entity, InteractionHand hand) {
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
        }
        return hasAnimation(entity, prefix) ? prefix : null;
    }

    private String selectSwingAnimation(PlayerGeoEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getEntity().getItemInHand(hand);
        String itemType = stack.isEmpty() ? null : InnerClassify.getItemType(stack);
        if (StringUtils.isNoneBlank(itemType)) {
            String classified = "swing:" + itemType;
            if (hasAnimation(entity, classified)) {
                return classified;
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

    private static boolean hasAnimation(PlayerGeoEntity entity, String animationName) {
        if (StringUtils.isBlank(animationName)) {
            return false;
        }
        var animation = entity.getAnimation(animationName);
        return animation != null && !animation.isEmpty();
    }
}
