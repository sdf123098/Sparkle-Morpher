package com.micaftic.morpher.core.api.item;

public record WeaponActionState(
        WeaponKind kind,
        TridentActionState trident,
        LanceActionState lance,
        MaceActionState mace,
        float speed,
        float verticalSpeed) {

    public static final WeaponActionState EMPTY = new WeaponActionState(
            WeaponKind.NONE,
            TridentActionState.EMPTY,
            LanceActionState.EMPTY,
            MaceActionState.EMPTY,
            0.0f,
            0.0f);
}
