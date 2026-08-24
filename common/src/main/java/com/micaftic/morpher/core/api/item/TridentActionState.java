package com.micaftic.morpher.core.api.item;

public record TridentActionState(
        boolean holding,
        boolean using,
        boolean throwing,
        boolean riptide,
        boolean attacking,
        float useTicks,
        float attackTicks) {

    public static final TridentActionState EMPTY = new TridentActionState(false, false, false, false, false, 0.0f, 0.0f);
}
