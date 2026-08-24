package com.micaftic.morpher.core.api.item;

public record MaceActionState(
        boolean holding,
        boolean falling,
        boolean canSmash,
        boolean smashing,
        boolean windBursting,
        boolean attacking,
        boolean riding,
        boolean fallFlying,
        float fallDistance,
        float verticalSpeed,
        float attackTicks,
        float smashProgress) {

    public static final MaceActionState EMPTY = new MaceActionState(
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            0.0f,
            0.0f,
            0.0f,
            0.0f);
}
