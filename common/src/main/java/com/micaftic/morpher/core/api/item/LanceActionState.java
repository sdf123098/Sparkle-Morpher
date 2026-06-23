package com.micaftic.morpher.core.api.item;

public record LanceActionState(
        boolean holding,
        boolean using,
        boolean charging,
        boolean jabbing,
        boolean lunging,
        boolean riding,
        boolean ridingCharge,
        boolean fallFlying,
        float useTicks,
        float attackTicks,
        float speed,
        float chargeProgress) {

    public static final LanceActionState EMPTY = new LanceActionState(
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
