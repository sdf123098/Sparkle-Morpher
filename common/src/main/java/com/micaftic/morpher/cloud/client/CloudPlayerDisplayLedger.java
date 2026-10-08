package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.display.PlayerDisplayState;
import java.util.*;

/** Per-world immutable inputs. Query retries cannot extend an acknowledged revision. */
public final class CloudPlayerDisplayLedger {
    private final Map<UUID, Entry> entries = new HashMap<>();
    private record Entry(long revision, CloudPlayerDisplayState display, long deadline) {}
    public boolean receive(UUID uuid, long revision, CloudPlayerDisplayState display, long nowNanos) {
        if (revision <= 0) return false;
        Entry old = entries.get(uuid);
        if (old != null && revision < old.revision) return false;
        long deadline = display == null ? nowNanos : display.deadlineNanos(nowNanos);
        if (old != null && old.revision == revision) {
            if (!Objects.equals(old.display, display)) return false;
            deadline = Math.min(deadline, old.deadline);
        }
        if (old == null && entries.size() >= 512) return false;
        entries.put(uuid, new Entry(revision, display, deadline));
        return true;
    }
    public PlayerDisplayState get(UUID uuid, String scope, String epoch, String dimension, long nowNanos) {
        Entry entry = entries.get(uuid);
        if (entry == null || entry.display == null || nowNanos - entry.deadline >= 0) return PlayerDisplayState.UNKNOWN;
        CloudPlayerDisplayState display = entry.display;
        return Objects.equals(scope, display.scopeId()) && Objects.equals(epoch, display.worldEpoch())
                && Objects.equals(dimension, display.dimensionId()) ? display.state() : PlayerDisplayState.UNKNOWN;
    }
    public void remove(UUID uuid) { entries.remove(uuid); }
    public void retain(Collection<UUID> loaded) { entries.keySet().retainAll(new HashSet<>(loaded)); }
    public void clear() { entries.clear(); }
}
