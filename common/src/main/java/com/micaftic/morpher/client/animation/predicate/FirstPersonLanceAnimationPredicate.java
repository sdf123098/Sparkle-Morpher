package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.entity.PlayerGeoEntity;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
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
        if (state.kind() != WeaponKind.LANCE) {
            return PlayState.STOP;
        }

        LanceActionState lance = state.lance();
        if (lance.using() || lance.charging() || lance.ridingCharge()) {
            if (player.getTicksUsingItem() == 1 && animatable.getPositionTracker().markProcessed(LANCE_USE_MARKER)) {
                event.getController().stopTransition();
            }
            String animation = selectAnimation(animatable, selectChargeNames(lance));
            return playIfPresent(event, animation, ILoopType.EDefaultLoopTypes.LOOP);
        }

        if (lance.jabbing() || lance.lunging()) {
            if (player.swingTime == 0 && player.swingingArm == InteractionHand.MAIN_HAND && animatable.getPositionTracker().markProcessed(LANCE_ATTACK_MARKER)) {
                event.getController().stopTransition();
            }
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

    private String[] selectChargeNames(LanceActionState lance) {
        if (lance.ridingCharge()) {
            return new String[]{"lance_riding_charge", "lance_charge", "use_mainhand:lance"};
        }
        if (lance.fallFlying()) {
            return new String[]{"lance_fall_flying_charge", "lance_charge", "use_mainhand:lance"};
        }
        return new String[]{"lance_charge", "use_mainhand:lance"};
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

    private PlayState playIfPresent(AnimationEvent<PlayerGeoEntity> event, String animationName, ILoopType loopType) {
        if (animationName == null) {
            return PlayState.STOP;
        }
        return IAnimationPredicate.playAnimationWithLoop(event, animationName, loopType);
    }
}
