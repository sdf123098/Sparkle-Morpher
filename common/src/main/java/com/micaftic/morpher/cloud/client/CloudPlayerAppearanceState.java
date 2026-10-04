package com.micaftic.morpher.cloud.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Per-player ordering and lifetime tokens for asynchronous model imports. */
public final class CloudPlayerAppearanceState {
    public record Snapshot(UUID playerId, long revision, CloudPlayerSelection selection, long generation) {}
    private final Map<UUID, Snapshot> players = new HashMap<>();
    private long generation;
    private long sequence;

    public Snapshot publish(UUID playerId, CloudPlayerSelection selection) {
        Snapshot previous = players.get(playerId);
        if (previous != null && Objects.equals(previous.selection(), selection)) return previous;
        Snapshot next = new Snapshot(Objects.requireNonNull(playerId), ++sequence, selection, generation);
        players.put(playerId, next);
        return next;
    }

    public boolean receive(UUID playerId, long revision, CloudPlayerSelection selection) {
        if (revision <= 0) return false;
        Snapshot previous = players.get(playerId);
        if (previous != null && revision <= previous.revision()) return false;
        players.put(playerId, new Snapshot(Objects.requireNonNull(playerId), revision, selection, generation));
        return true;
    }

    public Snapshot get(UUID playerId) { return players.get(playerId); }
    public Map<UUID, Snapshot> snapshot() { return Map.copyOf(players); }
    public boolean isCurrent(Snapshot token) { return token != null && token.equals(players.get(token.playerId())) && token.generation() == generation; }
    public boolean ownsAppearance(UUID playerId) { Snapshot current = get(playerId); return current != null && current.selection() != null; }
    public void remove(UUID playerId) { players.remove(playerId); }
    public void clear() { generation++; players.clear(); }
}
