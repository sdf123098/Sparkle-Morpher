package com.micaftic.morpher.peer;

/** Client-session choice, independent of room lifetime and connection retries. */
public final class PeerSharingPreference {
    private boolean enabled, explicitlyChosen;

    public boolean enabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        explicitlyChosen = true;
    }

    public void automaticRoomStarted() {
        if (!explicitlyChosen) enabled = true;
    }
}
