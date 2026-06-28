package com.micaftic.morpher.client.model;

public class HandLocatorProfile {

    public static final HandLocatorProfile YSM_AUTHORED = new HandLocatorProfile(false, false, SpecialHandLocatorProfile.NONE);
    public static final HandLocatorProfile VANILLA_EQUIPMENT = new HandLocatorProfile(true, true, SpecialHandLocatorProfile.NONE);

    private final boolean equipmentLocatorTransform;
    private final boolean vanillaUseOrientation;
    private final SpecialHandLocatorProfile specialHandLocatorProfile;

    public HandLocatorProfile(boolean equipmentLocatorTransform, boolean vanillaUseOrientation) {
        this(equipmentLocatorTransform, vanillaUseOrientation, SpecialHandLocatorProfile.NONE);
    }

    public HandLocatorProfile(boolean equipmentLocatorTransform, boolean vanillaUseOrientation, SpecialHandLocatorProfile specialHandLocatorProfile) {
        this.equipmentLocatorTransform = equipmentLocatorTransform;
        this.vanillaUseOrientation = vanillaUseOrientation;
        this.specialHandLocatorProfile = specialHandLocatorProfile == null ? SpecialHandLocatorProfile.NONE : specialHandLocatorProfile;
    }

    public static HandLocatorProfile ysmAuthored(SpecialHandLocatorProfile specialHandLocatorProfile) {
        if (specialHandLocatorProfile == null || specialHandLocatorProfile == SpecialHandLocatorProfile.NONE) {
            return YSM_AUTHORED;
        }
        return new HandLocatorProfile(false, false, specialHandLocatorProfile);
    }

    public boolean usesEquipmentLocatorTransform() {
        return this.equipmentLocatorTransform;
    }

    public boolean usesVanillaUseOrientation() {
        return this.vanillaUseOrientation;
    }

    public SpecialHandLocatorProfile getSpecialHandLocatorProfile() {
        return this.specialHandLocatorProfile;
    }

    public boolean usesSpecialHandLocatorSwordAnchor() {
        return this.specialHandLocatorProfile == SpecialHandLocatorProfile.HAND_LOCATOR_HIDDEN_BY_CARRYON;
    }
}
