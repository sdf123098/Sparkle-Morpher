package com.micaftic.morpher.core.compat.punchy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunchyModMatcherTest {

    @Test
    void matchesKnownPunchyIdentities() {
        assertTrue(PunchyModMatcher.matches("punchy", null));
        assertTrue(PunchyModMatcher.matches("punchy-fpa", null));
        assertTrue(PunchyModMatcher.matches(null, "Punchy!"));
        assertTrue(PunchyModMatcher.matches("some-mod", "PUNCHY"));
    }

    @Test
    void ignoresUnrelatedOrMissingIdentities() {
        assertFalse(PunchyModMatcher.matches(null, null));
        assertFalse(PunchyModMatcher.matches("", ""));
        assertFalse(PunchyModMatcher.matches("player-animator", "Player Animator"));
        assertFalse(PunchyModMatcher.matches("punch-out", "Punch Out"));
    }
}
