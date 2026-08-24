package com.micaftic.morpher.geckolib3.core.controller;

import java.util.HashSet;
import java.util.Set;

/**
 * Prevents a sound-bearing controller transition from retriggering while its
 * condition remains continuously true (for example, a locked world clock).
 */
final class SoundTransitionGate {

    private final Set<Long> heldTransitions = new HashSet<>();

    boolean allow(int sourceStateId, int targetStateId, boolean condition) {
        long key = ((long) sourceStateId << 32) ^ (targetStateId & 0xFFFFFFFFL);
        if (!condition) {
            this.heldTransitions.remove(key);
            return false;
        }
        return this.heldTransitions.add(key);
    }

    void clear() {
        this.heldTransitions.clear();
    }
}
