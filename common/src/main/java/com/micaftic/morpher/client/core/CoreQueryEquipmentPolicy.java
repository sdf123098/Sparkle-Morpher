package com.micaftic.morpher.client.core;

import java.util.Objects;
import java.util.function.Function;

/** d253 EquipmentUtil only applies cosmetic-armor overrides to player armor, never either hand. */
public final class CoreQueryEquipmentPolicy {
    private CoreQueryEquipmentPolicy() { }
    public enum Presence { ABSENT, PRESENT, UNKNOWN }
    /** Installed provider returns the effective equipment after its own optional-override fallback. */
    public static CoreQueryFunctions.Equipment select(Presence cosmeticArmor,CoreQueryFunctions.Slot slot,
            Function<CoreQueryFunctions.Slot,CoreQueryFunctions.Equipment> provider,
            Function<CoreQueryFunctions.Slot,CoreQueryFunctions.Equipment> vanilla) {
        Objects.requireNonNull(cosmeticArmor); Objects.requireNonNull(slot); Objects.requireNonNull(vanilla);
        boolean armor=slot!=CoreQueryFunctions.Slot.MAINHAND && slot!=CoreQueryFunctions.Slot.OFFHAND;
        if(armor) {
            if(cosmeticArmor==Presence.UNKNOWN) throw new IllegalStateException("Cosmetic armor presence must be sampled explicitly");
            if(cosmeticArmor==Presence.PRESENT) {
                if(provider==null) throw new IllegalStateException("Installed cosmetic armor requires an effective-equipment Host provider");
                return Objects.requireNonNull(provider.apply(slot));
            }
        }
        return Objects.requireNonNull(vanilla.apply(slot));
    }
}
