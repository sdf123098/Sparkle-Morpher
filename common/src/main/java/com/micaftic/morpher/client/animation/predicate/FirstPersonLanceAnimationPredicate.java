package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.SpmPlayerEntity;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.core.api.item.LanceActionState;
import com.micaftic.morpher.core.api.item.WeaponActionBridge;
import com.micaftic.morpher.core.api.item.WeaponActionState;
import com.micaftic.morpher.core.api.item.WeaponKind;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

public class FirstPersonLanceAnimationPredicate implements IAnimationPredicate<SpmPlayerEntity> {

    private static final int LANCE_ATTACK_MARKER = 101;
    private static final int LANCE_USE_MARKER = 102;

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<SpmPlayerEntity> event) {
        SpmPlayerEntity animatable = event.getAnimatable();
        LocalPlayer player = animatable.getEntity();
        WeaponActionState state = WeaponActionBridge.get(player, event.getPartialTick());
        if (!isLanceLike(state.kind())) {
            return HostPlayState.Decision.STOP;
        }

        LanceActionState lance = state.lance();
        if (lance.using() || lance.charging() || lance.ridingCharge()) {
            if (InputStateKey.getTicksUsingItem(player) == 1 && animatable.getPositionTracker().markProcessed(LANCE_USE_MARKER)) {
                event.getController().stopTransition();
            }
            String animation = selectAnimation(animatable, LanceAnimationTiming.selectChargeNames(lance));
            ClipSpec clip = animation == null ? null : animatable.getAnimation(animation);
            if (clip != null && event.getController() != null) {
                float useTicks = InputStateKey.getTicksUsingItem(player, event.getPartialTick());
                double speed = LanceAnimationTiming.computeChargePlaybackSpeed(
                        player.getMainHandItem(), useTicks, clip, event.getController().getCurrentAnimTimeSeconds());
                event.getController().setPlaybackSpeed(speed);
            }
            CodedLoopMode loopMode = resolveUseLoopMode(clip);
            return playIfPresent(event, animation, loopMode);
        }

        if (lance.jabbing() || lance.lunging()) {
            if (shouldRestartAttackAnimation(player) && animatable.getPositionTracker().markProcessed(LANCE_ATTACK_MARKER)) {
                event.getController().stopTransition();
            }
            String animation = lance.lunging()
                    ? selectAnimation(animatable, "lance_lunge", "lance_jab", "swing:lance")
                    : selectAnimation(animatable, "lance_jab", "swing:lance");
            return playIfPresent(event, animation, CodedLoopMode.PLAY_ONCE);
        }

        if (!lance.holding()) {
            return HostPlayState.Decision.STOP;
        }

        String animation = lance.riding()
                ? selectAnimation(animatable, "lance_riding_idle", "lance_stand", "hold_mainhand:lance")
                : selectAnimation(animatable, "lance_stand", "hold_mainhand:lance");
        ClipSpec holdClip = animation == null ? null : animatable.getAnimation(animation);
        return playIfPresent(event, animation, resolveUseLoopMode(holdClip));
    }

    private boolean shouldRestartAttackAnimation(LocalPlayer player) {
        if (InputStateKey.isLocalSwinging(InteractionHand.MAIN_HAND) && InputStateKey.getLocalSwingPulseAge() <= 1) {
            return true;
        }
        return InputStateKey.getSwingTicks(player, 0.0f) < 1.0f
                && InputStateKey.getSwingingHand(player) == InteractionHand.MAIN_HAND;
    }

    private boolean isLanceLike(WeaponKind kind) {
        return kind == WeaponKind.LANCE || kind == WeaponKind.SPEAR;
    }

    private String selectAnimation(SpmPlayerEntity entity, String... animationNames) {
        for (String animationName : animationNames) {
            ClipSpec animation = entity.getAnimation(animationName);
            if (animation != null && !animation.isEmpty()) {
                return animationName;
            }
        }
        return null;
    }

    private static CodedLoopMode resolveUseLoopMode(ClipSpec animation) {
        return animation != null && animation.loopMode() == CodedLoopMode.HOLD_ON_LAST_FRAME
                ? CodedLoopMode.HOLD_ON_LAST_FRAME
                : CodedLoopMode.LOOP;
    }

    private HostPlayState.Decision playIfPresent(HostActionEvent<SpmPlayerEntity> event, String animationName, CodedLoopMode loopMode) {
        if (animationName == null) {
            return HostPlayState.Decision.STOP;
        }
        return IAnimationPredicate.playAnimationWithLoop(event, animationName, loopMode);
    }
}
