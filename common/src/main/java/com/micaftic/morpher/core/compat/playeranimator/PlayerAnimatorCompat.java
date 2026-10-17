package com.micaftic.morpher.core.compat.playeranimator;

import net.minecraft.client.player.AbstractClientPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public final class PlayerAnimatorCompat {

    private static final String LEGACY_ACCESS = "dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess";
    private static final String CORE_ACCESS = "com.zigythebird.playeranim.api.PlayerAnimationAccess";
    private static final String BETTER_COMBAT_ATTACK_STACK = "net.bettercombat.client.animation.AttackAnimationStack";
    private static final ClassValue<Optional<Method>> FIRST_PERSON_PASS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                Method getter = type.getMethod("playerAnimLib$isFirstPersonPass");
                return getter.getReturnType() == boolean.class ? Optional.of(getter) : Optional.empty();
            } catch (NoSuchMethodException | LinkageError | RuntimeException ignored) {
                return Optional.empty();
            }
        }
    };

    private PlayerAnimatorCompat() {
    }

    public static boolean isLoaded() {
        return classExists(LEGACY_ACCESS) || classExists(CORE_ACCESS);
    }

    public static boolean isPlayerAnimated(AbstractClientPlayer player) {
        if (player == null) {
            return false;
        }
        return hasLegacyFirstPersonAnimation(player) || hasCoreFirstPersonAnimation(player);
    }

    /** PAL marks the actual render state, including attack fade-in/out frames. No camera guess is needed. */
    public static boolean isFirstPersonPass(Object renderState) {
        if (renderState == null) return false;
        Optional<Method> getter = FIRST_PERSON_PASS.get(renderState.getClass());
        if (getter.isEmpty()) return false;
        try {
            return Boolean.TRUE.equals(getter.get().invoke(renderState));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean hasLegacyFirstPersonAnimation(AbstractClientPlayer player) {
        try {
            Class<?> access = Class.forName(LEGACY_ACCESS, false, PlayerAnimatorCompat.class.getClassLoader());
            Method getLayer = access.getMethod("getPlayerAnimLayer", AbstractClientPlayer.class);
            Object layer = getLayer.invoke(null, player);
            return hasEnabledFirstPersonMode(layer);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean hasCoreFirstPersonAnimation(AbstractClientPlayer player) {
        try {
            Class<?> access = Class.forName(CORE_ACCESS, false, PlayerAnimatorCompat.class.getClassLoader());
            Class<?> attackStackType = Class.forName(BETTER_COMBAT_ATTACK_STACK, false, PlayerAnimatorCompat.class.getClassLoader());
            Field idField = attackStackType.getField("ID");
            Object layerId = idField.get(null);
            Method getLayer = null;
            for (Method method : access.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals("getPlayerAnimationLayer")
                        && parameters.length == 2
                        && parameters[0].isAssignableFrom(player.getClass())
                        && parameters[1].isInstance(layerId)) {
                    getLayer = method;
                    break;
                }
            }
            if (getLayer == null) {
                return false;
            }
            return hasEnabledFirstPersonMode(getLayer.invoke(null, player, layerId));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    static boolean hasEnabledFirstPersonMode(Object layer) {
        if (layer == null) {
            return false;
        }
        try {
            if (!Boolean.TRUE.equals(layer.getClass().getMethod("isActive").invoke(layer))) return false;
            Object mode;
            try {
                // Player Animation Library / PlayerAnimCore (current high-version branches).
                mode = layer.getClass().getMethod("getFirstPersonMode").invoke(layer);
            } catch (NoSuchMethodException legacy) {
                mode = layer.getClass().getMethod("getFirstPersonMode", float.class).invoke(layer, 0.0f);
            }
            if (mode == null) return false;
            try {
                return Boolean.TRUE.equals(mode.getClass().getMethod("isEnabled").invoke(mode));
            } catch (NoSuchMethodException legacy) {
                return Boolean.TRUE.equals(mode.getClass().getMethod("getEnabled").invoke(mode));
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, PlayerAnimatorCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
