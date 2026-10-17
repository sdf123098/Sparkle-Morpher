package com.micaftic.morpher.core.compat.playeranimator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerAnimatorCompatTest {
    @Test
    void identifiesPalFirstPersonRenderPassWithoutCameraOrAnimationTimingGuesses() {
        assertTrue(PlayerAnimatorCompat.isFirstPersonPass(new CurrentRenderState(true)));
        assertFalse(PlayerAnimatorCompat.isFirstPersonPass(new CurrentRenderState(false)));
    }

    @Test
    void optionalPalMarkerIsSafeWhenLibraryIsAbsent() {
        assertFalse(PlayerAnimatorCompat.isFirstPersonPass(null));
        assertFalse(PlayerAnimatorCompat.isFirstPersonPass(new Object()));
    }

    public record CurrentRenderState(boolean firstPerson) {
        public boolean playerAnimLib$isFirstPersonPass() { return firstPerson; }
    }

    @Test
    void recognizesCurrentPalFirstPersonApi() {
        assertTrue(PlayerAnimatorCompat.hasEnabledFirstPersonMode(new CurrentLayer(true, CurrentMode.THIRD_PERSON_MODEL)));
    }

    @Test
    void inactiveLayerDoesNotSuppressCustomPlayer() {
        assertFalse(PlayerAnimatorCompat.hasEnabledFirstPersonMode(new LegacyLayer(false, LegacyMode.ENABLED)));
        assertFalse(PlayerAnimatorCompat.hasEnabledFirstPersonMode(new CurrentLayer(false, CurrentMode.THIRD_PERSON_MODEL)));
    }

    @Test
    void retainsLegacyPlayerAnimatorSupport() {
        assertTrue(PlayerAnimatorCompat.hasEnabledFirstPersonMode(new LegacyLayer(true, LegacyMode.ENABLED)));
        assertFalse(PlayerAnimatorCompat.hasEnabledFirstPersonMode(new LegacyLayer(true, LegacyMode.DISABLED)));
        assertFalse(PlayerAnimatorCompat.hasEnabledFirstPersonMode(new CurrentLayer(true, CurrentMode.NONE)));
        assertFalse(PlayerAnimatorCompat.hasEnabledFirstPersonMode(null));
        assertFalse(PlayerAnimatorCompat.hasEnabledFirstPersonMode(new Object()));
    }

    public record CurrentLayer(boolean active, CurrentMode mode) {
        public boolean isActive() { return active; }
        public CurrentMode getFirstPersonMode() { return mode; }
    }
    public enum CurrentMode {
        NONE(false), THIRD_PERSON_MODEL(true);
        private final boolean enabled;
        CurrentMode(boolean enabled) { this.enabled = enabled; }
        public boolean isEnabled() { return enabled; }
    }
    public record LegacyLayer(boolean active, LegacyMode mode) {
        public boolean isActive() { return active; }
        public LegacyMode getFirstPersonMode(float partialTick) { return mode; }
    }
    public enum LegacyMode {
        DISABLED(false), ENABLED(true);
        private final boolean enabled;
        LegacyMode(boolean enabled) { this.enabled = enabled; }
        public boolean getEnabled() { return enabled; }
    }
}
