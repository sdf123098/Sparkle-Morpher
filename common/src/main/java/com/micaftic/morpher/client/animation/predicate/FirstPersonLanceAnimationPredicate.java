package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.PlayerGeoEntity;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.micaftic.morpher.core.api.item.LanceActionState;
import com.micaftic.morpher.core.api.item.WeaponActionBridge;
import com.micaftic.morpher.core.api.item.WeaponActionState;
import com.micaftic.morpher.core.api.item.WeaponKind;

public class FirstPersonLanceAnimationPredicate implements IAnimationPredicate<PlayerGeoEntity> {

    private static final int LANCE_ATTACK_MARKER = 101;
    private static final int LANCE_USE_MARKER = 102;

    @Override
    public PlayState predicate(AnimationEvent<PlayerGeoEntity> event, ExpressionEvaluator<?> evaluator) {
        PlayerGeoEntity animatable = event.getAnimatable();
        LocalPlayer player = animatable.getEntity();
        WeaponActionState state = WeaponActionBridge.get(player, event.getPartialTick());
        if (!isLanceLike(state.kind())) {
            return PlayState.STOP;
        }

        LanceActionState lance = state.lance();
        if (lance.using() || lance.charging() || lance.ridingCharge()) {
            if (InputStateKey.getTicksUsingItem(player) == 1 && animatable.getPositionTracker().markProcessed(LANCE_USE_MARKER)) {
                event.getController().stopTransition();
            }
            ItemStack itemStack = player.getMainHandItem();
            String animation = selectAnimation(animatable, LanceAnimationTiming.selectChargeNames(lance));
            applyKineticChargeAnimationTickOverride(event, itemStack, lance.useTicks(), animation == null ? null : animatable.getAnimation(animation));
            return playIfPresent(event, animation, ILoopType.EDefaultLoopTypes.LOOP);
        }

        if (lance.jabbing() || lance.lunging()) {
            if (shouldRestartAttackAnimation(player) && animatable.getPositionTracker().markProcessed(LANCE_ATTACK_MARKER)) {
                event.getController().stopTransition();
            }
            event.getController().setAnimationTickOverride(InputStateKey.getSwingAnimationTicks(player, event.getPartialTick()));
            String animation = lance.lunging()
                    ? selectAnimation(animatable, "lance_lunge", "lance_jab", "swing:lance")
                    : selectAnimation(animatable, "lance_jab", "swing:lance");
            return playIfPresent(event, animation, ILoopType.EDefaultLoopTypes.PLAY_ONCE);
        }

        if (!lance.holding()) {
            return PlayState.STOP;
        }

        String animation = lance.riding()
                ? selectAnimation(animatable, "lance_riding_idle", "lance_stand", "hold_mainhand:lance")
                : selectAnimation(animatable, "lance_stand", "hold_mainhand:lance");
        return playIfPresent(event, animation, ILoopType.EDefaultLoopTypes.LOOP);
    }

    private boolean shouldRestartAttackAnimation(LocalPlayer player) {
        if (InputStateKey.isLocalSwinging(InteractionHand.MAIN_HAND) && InputStateKey.getLocalSwingPulseAge() <= 1) {
            return true;
        }
        return player.swingTime == 0 && player.swingingArm == InteractionHand.MAIN_HAND;
    }

    private boolean isLanceLike(WeaponKind kind) {
        return kind == WeaponKind.LANCE || kind == WeaponKind.SPEAR;
    }

    private String selectAnimation(PlayerGeoEntity entity, String... animationNames) {
        for (String animationName : animationNames) {
            Animation animation = entity.getAnimation(animationName);
            if (animation != null && !animation.isEmpty()) {
                return animationName;
            }
        }
        return null;
    }

    private void applyKineticChargeAnimationTickOverride(AnimationEvent<PlayerGeoEntity> event, ItemStack itemStack, float useTicks, Animation animation) {
        float animationTick = LanceAnimationTiming.sampleKineticChargeAnimationTick(itemStack, useTicks, animation);
        if (animationTick >= 0.0f) {
            event.getController().setAnimationTickOverride(animationTick);
        }
    }

    private PlayState playIfPresent(AnimationEvent<PlayerGeoEntity> event, String animationName, ILoopType loopType) {
        if (animationName == null) {
            return PlayState.STOP;
        }
        return IAnimationPredicate.playAnimationWithLoop(event, animationName, loopType);
    }
}
