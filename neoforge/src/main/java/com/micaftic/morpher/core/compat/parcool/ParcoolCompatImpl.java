package com.micaftic.morpher.core.compat.parcool;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

/**
 * NeoForge-only ParCool (alRex-U, mod id {@code parcool}) compat bridge.
 *
 * <p>ParCool exposes parkour state through the {@code Parkourability} player
 * attachment ({@code com.alrex.parcool.common.attachment.common.Parkourability}):
 * {@code Parkourability.get(player)} → {@code getList()} → per-{@code Action}
 * {@code isDoing()}. The mod is optional and not on a Maven coordinate used by
 * this project, so access is fully reflective and hard-fails to "not doing" —
 * exactly the previous stub behaviour — whenever ParCool is absent or its
 * internals change. See docs/2026-09-09/FIX_PARCOOL_COMPAT_NEO_2026-09-09.md.</p>
 */
public final class ParcoolCompatImpl {

    private static final String MOD_ID = "parcool";
    private static final String PARKOURABILITY_CLASS =
            "com.alrex.parcool.common.attachment.common.Parkourability";

    private static final boolean LOADED = detectLoaded();
    private static final Optional<Class<?>> PARKOURABILITY_TYPE = loadClass(PARKOURABILITY_CLASS);
    private static final ConcurrentMap<String, Optional<Method>> ZERO_ARG_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Object, Direction> ACTION_DIRECTION_MEMORY = new ConcurrentHashMap<>();

    private ParcoolCompatImpl() {
    }

    /* ---------------------------------------------------------------- */
    /* Detection                                                         */
    /* ---------------------------------------------------------------- */

    private static boolean detectLoaded() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isLoaded() {
        return LOADED && PARKOURABILITY_TYPE.isPresent();
    }

    private static Optional<Class<?>> loadClass(String name) {
        try {
            return Optional.of(Class.forName(name, false, ParcoolCompatImpl.class.getClassLoader()));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    /* ---------------------------------------------------------------- */
    /* Reflective helpers (cached, exception-safe)                       */
    /* ---------------------------------------------------------------- */

    @Nullable
    private static Object parkourability(Player player) {
        if (player == null || !isLoaded()) {
            return null;
        }
        Optional<Method> getter = ZERO_ARG_METHODS.computeIfAbsent(
                PARKOURABILITY_CLASS + "#get",
                key -> findMethod(PARKOURABILITY_TYPE.get(), "get", Player.class));
        if (getter.isEmpty()) {
            return null;
        }
        try {
            return getter.get().invoke(null, player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Optional<Method> findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return Optional.of(owner.getMethod(name, parameterTypes));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static boolean booleanMethod(Object target, String className, String method) {
        if (target == null) {
            return false;
        }
        Optional<Method> m = ZERO_ARG_METHODS.computeIfAbsent(
                className + "#" + method, key -> findMethod(target.getClass(), method));
        if (m.isEmpty()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(m.get().invoke(target));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean anyDoing(Player player) {
        Object parkourability = parkourability(player);
        if (parkourability == null) {
            return false;
        }
        if (booleanMethod(parkourability, PARKOURABILITY_CLASS, "isDoingNothing")) {
            return false;
        }
        return true;
    }

    /* ---------------------------------------------------------------- */
    /* Public facade contract                                            */
    /* ---------------------------------------------------------------- */

    public static Optional<Pair<String, String>> getInCompatibleInfo() {
        return isLoaded() ? Optional.of(Pair.of("parcool", "ParCool")) : Optional.empty();
    }

    public static boolean isPlayerParcooling(Player player) {
        return anyDoing(player);
    }

    /**
     * 返回当前正在执行的 ParCool 动作对应的内置动画名（{@code parcool:*}），
     * 未加载 / 无动作 / 未知动作时返回 {@code null}。
     */
    @Nullable
    public static String getActionName(Player player) {
        Object parkourability = parkourability(player);
        if (parkourability == null) {
            return null;
        }
        Optional<Method> listMethod = ZERO_ARG_METHODS.computeIfAbsent(
                PARKOURABILITY_CLASS + "#getList", key -> findMethod(parkourability.getClass(), "getList"));
        if (listMethod.isEmpty()) {
            return null;
        }
        List<?> actions;
        try {
            actions = (List<?>) listMethod.get().invoke(parkourability);
        } catch (Throwable ignored) {
            return null;
        }
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        String doing = null;
        for (Object action : actions) {
            if (action == null) {
                continue;
            }
            boolean isDoing = booleanMethod(action, action.getClass().getName(), "isDoing");
            String simple = action.getClass().getSimpleName();
            // ChargeJump reports "charging" while not doing; treat it as active so
            // the crouch-charge animation can play before the jump is released.
            boolean charging = "ChargeJump".equals(simple)
                    && booleanMethod(action, action.getClass().getName(), "isCharging");
            if (!isDoing && !charging) {
                // Forget a previously remembered dodge/roll direction once the
                // action instance finishes, so the next one samples fresh inputs.
                ACTION_DIRECTION_MEMORY.remove(action);
                continue;
            }
            String mapped = mapAction(player, simple, action);
            if (mapped != null) {
                doing = mapped;
                break;
            }
        }
        return doing;
    }

    public static Optional<BiFunction<String, CustomPlayerEntity, IAnimationController<CustomPlayerEntity>>> getControllerFactory() {
        if (!isLoaded()) {
            return Optional.empty();
        }
        return Optional.of((animationEntryKey, entity) ->
                new com.micaftic.morpher.geckolib3.core.controller.CompositeAnimationController<>(
                        entity, animationEntryKey, 0.0f,
                        new com.micaftic.morpher.client.animation.predicate.PlayerSpecialAnimationPredicate()));
    }

    public static void registerBindings(CtrlBinding binding) {
        if (!isLoaded()) {
            // Keep the variables resolvable for models that reference ctrl.parcool_*,
            // even when ParCool is absent (mirrors the historical placeholder).
            binding.livingEntityVar("parcool_loaded", ctx -> false);
            binding.livingEntityVar("parcool_doing", ctx -> false);
            return;
        }
        binding.livingEntityVar("parcool_loaded", ctx -> true);
        binding.livingEntityVar("parcool_doing", ctx -> ctx.entity() instanceof Player player && isPlayerParcooling(player));
    }

    /* ---------------------------------------------------------------- */
    /* Action → built-in animation mapping                               */
    /* ---------------------------------------------------------------- */

    @Nullable
    private static String mapAction(Player player, String simpleName, Object action) {
        switch (simpleName) {
            case "Dodge":
                return directionSuffixed(player, action, "dodge");
            case "Roll":
                return directionSuffixed(player, action, "roll");
            case "WallJump":
                return wallJump(player);
            case "ClingToCliff":
                return clingToCliff(action);
            case "WallSlide":
                return wallSlide(player, action);
            case "HorizontalWallRun":
                return horizontalWallRun(player, action);
            case "Vault":
                return vault(action);
            case "Flipping":
                return flipping(player);
            case "FastRun":
                return "parcool:fast_running";
            case "ClimbUp":
                return "parcool:climb_up";
            case "VerticalWallRun":
                return "parcool:vertical_wall_run";
            case "Dive":
                return "parcool:dive_animation_host";
            case "FastSwim":
                return "parcool:fast_swim";
            case "Slide":
                return "parcool:sliding";
            case "JumpFromBar":
                return "parcool:jump_from_bar";
            case "RideZipline":
                return "parcool:ride_zipline";
            case "Tap":
                return "parcool:tap";
            case "CatLeap":
                return "parcool:cat_leap";
            case "HangDown":
                return "parcool:hang";
            case "ChargeJump":
                return chargeJump(action);
            default:
                return null;
        }
    }

    /* --- direction helpers (mirror ParCool animator naming) --- */

    @Nullable
    private static String directionSuffixed(Player player, Object action, String base) {
        // ParCool samples the movement keys when the action starts and keeps that
        // direction for its whole duration; once the keys are released the inputs
        // go back to ~0, so the direction must be remembered per action instance
        // (instances are long-lived session objects) rather than re-inferred per frame.
        Direction dir = ACTION_DIRECTION_MEMORY.computeIfAbsent(action, key -> {
            Direction inferred = movementDirection(player);
            return inferred != null ? inferred : Direction.FRONT;
        });
        switch (dir) {
            case FRONT:
                return "parcool:" + base + "_front";
            case BACK:
                return "parcool:" + base + "_back";
            case LEFT:
                return "parcool:" + base + "_left";
            case RIGHT:
                return "parcool:" + base + "_right";
        }
        return "parcool:" + base + "_front";
    }

    @Nullable
    private static String clingToCliff(Object action) {
        String facing = enumName(action, "getFacingDirection");
        if (facing == null) {
            return "parcool:cling_to_cliff";
        }
        switch (facing) {
            case "RightAgainstWall":
                return "parcool:cling_to_cliff_right";
            case "LeftAgainstWall":
                return "parcool:cling_to_cliff_left";
            default:
                return "parcool:cling_to_cliff";
        }
    }

    @Nullable
    private static String wallSlide(Player player, Object action) {
        // WallSlide leans toward a wall; left/right from the wall-side vector.
        String side = wallSide(action, "getLeanedWallDirection");
        if (side == null) {
            Direction dir = movementDirection(player);
            side = dir == Direction.LEFT ? "left" : dir == Direction.RIGHT ? "right" : "right";
        }
        return "parcool:wall_slide_" + side;
    }

    @Nullable
    private static String horizontalWallRun(Player player, Object action) {
        // Runs along a wall; pick by wall-side vector when available.
        String side = wallSide(action, "getRunningWallDirection");
        if (side == null) {
            side = wallSide(action, "getLeanedWallDirection");
        }
        if (side == null) {
            Direction dir = movementDirection(player);
            side = dir == Direction.LEFT ? "left" : dir == Direction.RIGHT ? "right" : "right";
        }
        return "parcool:horizontal_wall_run_" + side;
    }

    @Nullable
    private static String vault(Object action) {
        String current = enumName(action, "getCurrentAnimation");
        if (current == null) {
            return null;
        }
        switch (current) {
            case "SPEED_VAULT_RIGHT":
                return "parcool:speed_vault_right";
            case "SPEED_VAULT_LEFT":
                return "parcool:speed_vault_left";
            case "KONG_VAULT":
                return "parcool:kong_vault";
            default:
                return null;
        }
    }

    @Nullable
    private static String flipping(Player player) {
        // Flipping (front/back somersault) direction follows player movement input.
        Direction dir = movementDirection(player);
        return dir == Direction.BACK ? "parcool:flipping_back" : "parcool:flipping_front";
    }

    @Nullable
    private static String chargeJump(Object action) {
        // ChargeJump: charging (chargeTick>0 && !doing) plays the crouch charge,
        // a triggered jump plays the launched animation.
        if (booleanMethod(action, action.getClass().getName(), "isDoing")) {
            return "parcool:charge_jump";
        }
        return booleanMethod(action, action.getClass().getName(), "isCharging")
                ? "parcool:jump_charging" : null;
    }

    @Nullable
    private static String wallJump(Player player) {
        // Left/right relative to the wall is decided internally by ParCool; mirror
        // the strafe input when it points sideways, otherwise use the backward jump.
        Direction dir = movementDirection(player);
        if (dir == Direction.LEFT) {
            return "parcool:wall_jump_left";
        }
        if (dir == Direction.RIGHT) {
            return "parcool:wall_jump_right";
        }
        return "parcool:backward_wall_jump";
    }

    /* --- low-level direction extraction --- */

    @Nullable
    private static String enumName(Object action, String getter) {
        Optional<Method> m = ZERO_ARG_METHODS.computeIfAbsent(
                action.getClass().getName() + "#" + getter, key -> findMethod(action.getClass(), getter));
        if (m.isEmpty()) {
            return null;
        }
        try {
            Object value = m.get().invoke(action);
            if (value instanceof Enum<?> e) {
                return e.name();
            }
            return value == null ? null : value.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static String wallSide(Object action, String getter) {
        Optional<Method> m = ZERO_ARG_METHODS.computeIfAbsent(
                action.getClass().getName() + "#" + getter, key -> findMethod(action.getClass(), getter));
        if (m.isEmpty()) {
            return null;
        }
        try {
            Object vec = m.get().invoke(action);
            if (vec == null) {
                return null;
            }
            // Vec3 in 1.21.1: net.minecraft.world.phys.Vec3 with x()/z().
            Method x = vec.getClass().getMethod("x");
            Method z = vec.getClass().getMethod("z");
            double dx = (double) x.invoke(vec);
            double dz = (double) z.invoke(vec);
            if (dx == 0 && dz == 0) {
                return null;
            }
            // Wall-side vector in world space: map dominant axis sign to left/right.
            return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? "right" : "left") : (dz >= 0 ? "right" : "left");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 玩家水平移动输入 → 方向。ParCool 的 dodge/roll 起始方向同样来自移动按键
     * （前后左右），此处用相同的输入信号；若输入不可用则回退 null（由调用方给默认）。
     */
    @Nullable
    private static Direction movementDirection(Player player) {
        try {
            if (player == null) {
                return null;
            }
            float forward = player.zza; // -1 back .. 1 front
            float strafe = player.xxa;  // -1 left .. 1 right
            if (Math.abs(forward) >= Math.abs(strafe)) {
                if (forward > 0.01f) {
                    return Direction.FRONT;
                }
                if (forward < -0.01f) {
                    return Direction.BACK;
                }
            }
            if (strafe > 0.01f) {
                return Direction.RIGHT;
            }
            if (strafe < -0.01f) {
                return Direction.LEFT;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private enum Direction {
        FRONT, BACK, LEFT, RIGHT
    }
}
