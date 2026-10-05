package com.micaftic.morpher.cloud.client;

/** Separates a new controller entry from a heartbeat or a changed shared variable. */
public final class CloudControllerMotionCursor {
    private CloudPlayerMotion.Controller last;
    public boolean needsTransition(CloudPlayerMotion.Controller next) {
        return last == null || !last.state().equals(next.state()) || last.startedAtUnixMs() != next.startedAtUnixMs();
    }
    public boolean needsVariables(CloudPlayerMotion.Controller next) {
        return last == null || !last.variables().equals(next.variables()) || needsTransition(next);
    }
    public void accept(CloudPlayerMotion.Controller next) { last = next; }
    public void clear() { last = null; }
}
