package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

public final class MaidMovementAnimationPredicate implements IAnimationPredicate<MaidCapability> {
    private static final float MIN_SPEED = 0.05f;

    @Override
    public PlayState predicate(AnimationEvent<MaidCapability> event, ExpressionEvaluator<?> evaluator) {
        LivingEntity maid = event.getAnimatable().getEntity();
        if (maid == null || maid.getVehicle() != null) {
            return PlayState.STOP;
        }
        String animation;
        ILoopType loop = ILoopType.EDefaultLoopTypes.LOOP;
        if (maid.isDeadOrDying()) {
            animation = "death";
            loop = ILoopType.EDefaultLoopTypes.PLAY_ONCE;
        } else if (maid.isSleeping()) {
            animation = "sleep";
        } else if (maid.isSwimming()) {
            animation = "swim";
        } else if (maid.onClimbable()) {
            double dy = maid.getY() - maid.yo;
            animation = dy > 0.001 ? "ladder_up" : dy < -0.001 ? "ladder_down" : "ladder_stillness";
        } else if (maid.isInWater() && !maid.onGround()) {
            animation = "swim_stand";
        } else if (maid.hurtTime > 0) {
            animation = "attacked";
            loop = ILoopType.EDefaultLoopTypes.PLAY_ONCE;
        } else if (!maid.onGround()) {
            animation = "jump";
        } else if (maid.getPose() == Pose.CROUCHING && Math.abs(event.getLimbSwingAmount()) > MIN_SPEED) {
            animation = "sneak";
        } else if (maid.getPose() == Pose.CROUCHING) {
            animation = "sneaking";
        } else if (maid.isSprinting()) {
            animation = "run";
        } else if (Math.abs(event.getLimbSwingAmount()) > MIN_SPEED) {
            animation = "walk";
        } else {
            animation = "idle";
        }
        return IAnimationPredicate.playAnimationWithLoop(event, animation, loop);
    }
}
