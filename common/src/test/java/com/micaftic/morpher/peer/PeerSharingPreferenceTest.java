package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeerSharingPreferenceTest {
    @Test void choiceBeforeConnectionSurvivesAutomaticRoomStartupAndReconnect() {
        var preference = new PeerSharingPreference();
        preference.setEnabled(true);
        assertTrue(preference.enabled());
        preference.automaticRoomStarted();
        preference.automaticRoomStarted();
        assertTrue(preference.enabled());
    }

    @Test void explicitOptOutIsNeverOverriddenByAnAutomaticRoom() {
        var preference = new PeerSharingPreference();
        preference.setEnabled(false);
        preference.automaticRoomStarted();
        assertFalse(preference.enabled());
        preference.setEnabled(true);
        preference.setEnabled(false);
        preference.automaticRoomStarted();
        assertFalse(preference.enabled());
    }

    @Test void automaticRoomsKeepTheirDefaultUntilTheUserChooses() {
        var preference = new PeerSharingPreference();
        assertFalse(preference.enabled());
        preference.automaticRoomStarted();
        assertTrue(preference.enabled());
        preference.setEnabled(false);
        assertFalse(preference.enabled());
    }
}
