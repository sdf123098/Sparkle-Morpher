package com.micaftic.morpher.cloud.client;

/** Unknown control ownership falls back; caller passes only the native controlling passenger. */
public final class VehicleAppearancePolicy {
    public enum Source { INDEPENDENT, CONTROLLING_PASSENGER, VANILLA }
    private VehicleAppearancePolicy() {}
    public static Source choose(boolean usesVanillaRenderer, boolean hasAccessibleBinding,
            boolean hasReliableControllingPassengerBundle) {
        if (usesVanillaRenderer) return Source.VANILLA;
        if (hasAccessibleBinding) return Source.INDEPENDENT;
        return hasReliableControllingPassengerBundle ? Source.CONTROLLING_PASSENGER : Source.VANILLA;
    }
}
