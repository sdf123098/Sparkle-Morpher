package com.micaftic.morpher.core.target;

import java.util.EnumSet;
import java.util.Set;

/** Immutable capability policy; target-specific adapters can further restrict it. */
public final class TargetCapabilities {
    private TargetCapabilities() {}
    public static Set<TargetCapability> forType(TargetType type) {
        return switch (type == null ? TargetType.UNKNOWN : type) {
            case PLAYER, FAKE_PLAYER, MAID -> Set.of(TargetCapability.MODEL, TargetCapability.PARTS,
                    TargetCapability.TEXTURE, TargetCapability.TRANSFORM, TargetCapability.ANIMATION);
            case UNKNOWN, UNSUPPORTED -> Set.of();
        };
    }
    public static boolean supports(TargetType type, TargetCapability capability) {
        return capability != null && forType(type).contains(capability);
    }
    public static EnumSet<TargetCapability> mutableCopy(TargetType type) {
        return forType(type).isEmpty() ? EnumSet.noneOf(TargetCapability.class) : EnumSet.copyOf(forType(type));
    }
}
