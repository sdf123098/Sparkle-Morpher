package com.micaftic.morpher.cloud.client;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Monotonic, per-player receiver. A late response cannot replace a newer player's action. */
public final class CloudPlayerMotionLedger {
    private record Entry(long revision, CloudPlayerMotion motion) {}
    private final Map<UUID, Entry> peers = new ConcurrentHashMap<>();
    public boolean receive(UUID id, long revision, CloudPlayerMotion motion) {
        if (revision <= 0) return false; boolean[] accepted = {false};
        peers.compute(id, (key, old) -> {
            if (old != null && old.revision() >= revision) return old;
            accepted[0] = true; return new Entry(revision, motion);
        });
        return accepted[0];
    }
    public CloudPlayerMotion get(UUID id) { Entry entry = peers.get(id); return entry == null ? null : entry.motion(); }
    public void remove(UUID id) { peers.remove(id); }
    public void clear() { peers.clear(); }
}
