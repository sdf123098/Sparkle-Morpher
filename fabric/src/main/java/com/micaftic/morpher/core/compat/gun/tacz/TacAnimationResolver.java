package com.micaftic.morpher.core.compat.gun.tacz;

import java.util.function.Predicate;

/** Backend-independent gun animation selection; no optional mod classes are loaded here. */
public final class TacAnimationResolver {
    private TacAnimationResolver() {}

    public static String holdPrefix(boolean crawling, boolean moving, boolean aiming, boolean sprinting) {
        if (crawling) return moving ? "tac:climb:" : "tac:climbing:";
        if (aiming) return "tac:aim:";
        return sprinting ? "tac:run:" : "tac:hold:";
    }

    public static String actionPrefix(boolean crawling, boolean moving, boolean aiming,
                                      boolean reloading, boolean melee, long shootCooldown) {
        if (reloading) return "tac:reload:";
        if (melee) return "tac:melee:";
        if (shootCooldown <= 0) return null;
        if (crawling && !moving) return "tac:climbing:fire:";
        return aiming ? "tac:aim:fire:" : "tac:hold:fire:";
    }

    public static String select(String prefix, String type, String override, Predicate<String> available) {
        if (override != null && !override.isBlank() && available.test(override)) return override;
        String category = "pistol".equals(type) ? "pistol" : "rpg".equals(type) ? "rpg" : "rifle";
        String animation = prefix + category;
        return available.test(animation) ? animation : null;
    }
}
