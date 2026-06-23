package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.compat.parcool.ParcoolCompat;
import com.micaftic.morpher.core.compat.slashblade.SlashBladeCompat;
import com.micaftic.morpher.core.compat.gun.swarfare.SWarfareCompat;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.core.compat.create.CreateCompat;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

public class AnimationManager implements IAnimationPredicate<CustomPlayerEntity> {

    private static final ReferenceArrayList<AnimationState<Player, CustomPlayerEntity>>[] data = new ReferenceArrayList[Priority.LOWEST + 1];
    private static int missingAnimationLogs;
    private static int selectedAnimationLogs;

    static {
        for (int i = 0; i < data.length; i++) {
            data[i] = new ReferenceArrayList<>(6);
        }
    }

    public static void register(AnimationState<Player, CustomPlayerEntity> state) {
        data[state.getPriority()].add(state);
    }

    @Override
    public PlayState predicate(AnimationEvent<CustomPlayerEntity> event, ExpressionEvaluator<?> evaluator) {
        AnimationRegister.registerAnimationState();
        Player player = event.getAnimatable().getEntity();
        if (player == null) {
            return PlayState.STOP;
        }
        if (event.getAnimatable() instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        if (ParcoolCompat.isPlayerParcooling(player)) {
            return PlayState.STOP;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle != null && vehicle.isAlive()) {
            return PlayState.STOP;
        }
        if (CreateCompat.isPlayerOnCreateContraption(player)) {
            return IAnimationPredicate.predicate(event, "parcool:ride_zipline");
        }
        for (int i = Priority.HIGHEST; i <= Priority.LOWEST; i++) {
            for (AnimationState<Player, CustomPlayerEntity> animationState : data[i]) {
                if (animationState.getPredicate().test(player, event)) {
                    String name = animationState.getAnimationName();
                    ILoopType loopType = animationState.getLoopType();
                    if (event.getAnimatable().getAnimation(name) == null) {
                        logMissingAnimation(event, name);
                        continue;
                    }
                    logSelectedAnimation(event, name);
                    PlayState slashBladePlayState = SlashBladeCompat.handleSlashBladeAnim(player, event, name, loopType);
                    if (slashBladePlayState != null) {
                        return slashBladePlayState;
                    }
                    PlayState taczPlayState = TacCompat.handleTaczAnimState(player, event, name, loopType);
                    if (taczPlayState == null) {
                        taczPlayState = SWarfareCompat.handleTaczAnim(player, event, name, loopType);
                    }
                    return Objects.requireNonNullElseGet(taczPlayState, () -> IAnimationPredicate.playAnimationWithLoop(event, name, loopType));
                }
            }
        }
        return PlayState.STOP;
    }

    private static void logMissingAnimation(AnimationEvent<CustomPlayerEntity> event, String name) {
        if (missingAnimationLogs++ >= 40) {
            return;
        }
        CustomPlayerEntity animatable = event.getAnimatable();
        YesSteveModel.LOGGER.info("[SM-ANIM] main selected missing animation={} model={} hasIdle={} hasWalk={} hasRun={} limbSwingAmount={} moving={}",
                name,
                animatable.getModelId(),
                animatable.getAnimation("idle") != null,
                animatable.getAnimation("walk") != null,
                animatable.getAnimation("run") != null,
                event.getLimbSwingAmount(),
                event.isMoving());
    }

    private static void logSelectedAnimation(AnimationEvent<CustomPlayerEntity> event, String name) {
        if (selectedAnimationLogs++ >= 40) {
            return;
        }
        CustomPlayerEntity animatable = event.getAnimatable();
        YesSteveModel.LOGGER.info("[SM-ANIM] main selected animation={} model={} limbSwingAmount={} moving={}",
                name,
                animatable.getModelId(),
                event.getLimbSwingAmount(),
                event.isMoving());
    }
}
