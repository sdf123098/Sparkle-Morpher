package com.micaftic.morpher.core.compat.punchy;

import java.util.Locale;

/**
 * Loader-independent match rule for Punchy! detection.
 *
 * <p>Punchy! ships without source and its loader id is not stable across distributions
 * ({@code punchy}, {@code punchy-fpa}), so detection matches a substring in either the mod id or
 * the display name. Kept separate from the loader scans so the rule itself is unit-testable.
 */
public final class PunchyModMatcher {

    private static final String NEEDLE = "punchy";

    private PunchyModMatcher() {
    }

    public static boolean matches(String modId, String displayName) {
        return containsNeedle(modId) || containsNeedle(displayName);
    }

    private static boolean containsNeedle(String candidate) {
        return candidate != null && candidate.toLowerCase(Locale.ROOT).contains(NEEDLE);
    }
}
