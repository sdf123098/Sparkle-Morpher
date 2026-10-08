package com.micaftic.morpher.core.display;

import java.util.Map;

/** Render input only. Null means the publisher could not observe this field. */
public record PlayerDisplayState(Integer experienceLevel, Float health, Float maxHealth,
        Integer foodLevel, Map<String, Integer> effectAmplifiers, Boolean flying,
        Float strafeInput, Float verticalInput, Float forwardInput, Boolean shieldBlocking) {
    public static final PlayerDisplayState UNKNOWN = new PlayerDisplayState(null, null, null,
            null, null, null, null, null, null, null);
    public PlayerDisplayState {
        if (experienceLevel != null && experienceLevel < 0) throw new IllegalArgumentException("experience level");
        if (foodLevel != null && (foodLevel < 0 || foodLevel > 20)) throw new IllegalArgumentException("food level");
        check(health, 0, 1_000_000); check(maxHealth, 0, 1_000_000);
        check(strafeInput, -1, 1); check(verticalInput, -1, 1); check(forwardInput, -1, 1);
        if (effectAmplifiers != null) {
            effectAmplifiers = Map.copyOf(effectAmplifiers);
            if (effectAmplifiers.size() > 256) throw new IllegalArgumentException("effect count");
            effectAmplifiers.forEach((id, value) -> {
                if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.length() > 256
                        || value < 1 || value > 256) throw new IllegalArgumentException("effect amplifier");
            });
        }
    }
    private static void check(Float value, float min, float max) {
        if (value != null && (!Float.isFinite(value) || value < min || value > max))
            throw new IllegalArgumentException("non-finite or out-of-range display value");
    }
}
