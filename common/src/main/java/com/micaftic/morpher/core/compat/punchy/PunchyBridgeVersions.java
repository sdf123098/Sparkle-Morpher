package com.micaftic.morpher.core.compat.punchy;

import java.util.Set;

/**
 * Punchy! versions whose first-person arm render path has been inspected and whose
 * {@code PunchyArmRenderer.renderArm} shape matches the arm bridge's HEAD injection.
 *
 * <p>The bridge mixin is only applied for these versions. A future Punchy! build with a different
 * arm render path must be re-inspected before being added here; the mixin never applies blindly.
 */
public final class PunchyBridgeVersions {

    private static final Set<String> SUPPORTED = Set.of("2.8g");

    private PunchyBridgeVersions() {
    }

    public static boolean isSupported(String version) {
        if (version == null) {
            return false;
        }
        String normalized = version.trim();
        int metadata = normalized.indexOf('+');
        if (metadata >= 0) {
            normalized = normalized.substring(0, metadata);
        }
        return SUPPORTED.contains(normalized);
    }
}
