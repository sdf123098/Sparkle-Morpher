package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.core.compat.parcool.ParcoolCompat;
import com.micaftic.morpher.core.compat.slashblade.SlashBladeCompat;
import com.micaftic.morpher.core.compat.gun.swarfare.SWarfareCompat;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.geckolib3.core.EntityFrameStateTracker;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.core.compat.create.CreateCompat;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

public class AnimationManager implements IAnimationPredicate<CustomPlayerEntity> {

    private static final ReferenceArrayList<AnimationState<Player, CustomPlayerEntity>>[] data = new ReferenceArrayList[Priority.LOWEST + 1];

    static {
        for (int i = 0; i < data.length; i++) {
            data[i] = new ReferenceArrayList<>(6);
        }
    }

    private static final List<PlayerActionState> REGISTERED_STATES = List.of(
            PlayerActionState.DEATH,
            PlayerActionState.RIPTIDE,
            PlayerActionState.SLEEP,
            PlayerActionState.SWIM,
            PlayerActionState.CLIMB,
            PlayerActionState.CLIMBING,
            PlayerActionState.LADDER_UP,
            PlayerActionState.LADDER_STILLNESS,
            PlayerActionState.LADDER_DOWN,
            PlayerActionState.ELYTRA_FLY,
            PlayerActionState.FLY,
            PlayerActionState.SWIM_STAND,
            PlayerActionState.ATTACKED,
            PlayerActionState.JUMP,
            PlayerActionState.SNEAK,
            PlayerActionState.SNEAKING,
            PlayerActionState.RUN,
            PlayerActionState.WALK,
            PlayerActionState.IDLE
    );

    public static void registerDefaultStates() {
        register(PlayerActionState.DEATH, ILoopType.EDefaultLoopTypes.PLAY_ONCE, Priority.HIGHEST);
        register(PlayerActionState.RIPTIDE, Priority.HIGHEST);
        register(PlayerActionState.SLEEP, Priority.HIGHEST);
        register(PlayerActionState.SWIM, Priority.HIGHEST);
        register(PlayerActionState.CLIMB, Priority.HIGHEST);
        register(PlayerActionState.CLIMBING, Priority.HIGHEST);
        register(PlayerActionState.LADDER_UP, Priority.HIGHEST);
        register(PlayerActionState.LADDER_STILLNESS, Priority.HIGHEST);
        register(PlayerActionState.LADDER_DOWN, Priority.HIGHEST);
        register(PlayerActionState.ELYTRA_FLY, Priority.HIGH);
        register(PlayerActionState.FLY, Priority.HIGH);
        register(PlayerActionState.SWIM_STAND, Priority.NORMAL);
        register(PlayerActionState.ATTACKED, ILoopType.EDefaultLoopTypes.PLAY_ONCE, 2);
        register(PlayerActionState.JUMP, Priority.NORMAL);
        register(PlayerActionState.SNEAK, Priority.NORMAL);
        register(PlayerActionState.SNEAKING, Priority.NORMAL);
        register(PlayerActionState.RUN, Priority.LOW);
        register(PlayerActionState.WALK, Priority.LOW);
        register(PlayerActionState.IDLE, Priority.LOWEST);
    }

    static List<PlayerActionState> registeredStates() {
        return REGISTERED_STATES;
    }

    private static void register(PlayerActionState state, ILoopType loopType, int priority) {
        register(state.animationName(), loopType, priority, (player, event) -> isState(state, player, event));
    }

    private static void register(PlayerActionState state, int priority) {
        register(state, ILoopType.EDefaultLoopTypes.LOOP, priority);
    }

    private static void register(String animationName, ILoopType loopType, int priority,
                                 BiPredicate<Player, AnimationEvent<CustomPlayerEntity>> predicate) {
        register(new AnimationState<>(animationName, loopType, priority, predicate));
    }

    private static boolean isState(PlayerActionState state, Player player, AnimationEvent<CustomPlayerEntity> event) {
        CustomPlayerEntity animatable = event.getAnimatable();
        EntityFrameStateTracker<?> tracker = animatable.getPositionTracker();
        String cached = tracker.getCachedControllerState();
        if (cached == null) {
            cached = ControllerActionResolver.resolve(animatable, player, event);
            tracker.setCachedControllerState(cached);
        }
        return state.animationName().equals(cached);
    }

    public static void register(AnimationState<Player, CustomPlayerEntity> state) {
        data[state.getPriority()].add(state);
    }

    @Override
    public PlayState predicate(AnimationEvent<CustomPlayerEntity> event, ExpressionEvaluator<?> evaluator) {
        Player player = event.getAnimatable().getEntity();
        if (player == null) {
            return PlayState.STOP;
        }
        if (event.getAnimatable() instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        ModelAssembly modelAssembly = event.getAnimatable().getModelAssembly();
        PlayerModelBundle animationBundle = modelAssembly == null ? null : modelAssembly.getAnimationBundle();
        if (animationBundle == null) {
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
}
