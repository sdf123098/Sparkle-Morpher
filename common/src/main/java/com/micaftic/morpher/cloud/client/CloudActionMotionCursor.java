package com.micaftic.morpher.cloud.client;

/** Heartbeats and variable edits retain the action clock; a new event restarts even the same clip. */
public final class CloudActionMotionCursor {
    private String eventId;
    public boolean update(CloudPlayerMotion motion) {
        if (motion == null) { clear(); return false; }
        if (motion.eventId().equals(eventId)) return false;
        eventId = motion.eventId();
        return true;
    }
    public void clear() { eventId = null; }
}
