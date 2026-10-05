package com.micaftic.morpher.cloud.client;

import java.util.*;

/** A stable event id survives heartbeats; a new click, even on the same clip, creates a new event. */
public final class CloudPlayerMotionState {
    private CloudPlayerMotion snapshot = new CloudPlayerMotion(UUID.randomUUID().toString(), "", 0, Map.of(), List.of(), Map.of());
    private void replaceSnapshot(CloudPlayerMotion candidate) { candidate.toJson(); snapshot = candidate; }
    public synchronized CloudPlayerMotion snapshot() { return snapshot; }
    public synchronized void play(String key, long now) {
        replaceSnapshot(new CloudPlayerMotion(UUID.randomUUID().toString(), key, now, snapshot.roaming(), snapshot.expressions(), snapshot.controllers()));
    }
    public void stop(long now) { play("", now); }
    public synchronized void roaming(Map<String, Float> changes) {
        Map<String, Float> merged = new LinkedHashMap<>(snapshot.roaming()); merged.putAll(changes);
        merged = CloudPlayerMotion.numbers(merged, 32);
        if (!merged.equals(snapshot.roaming())) replaceSnapshot(new CloudPlayerMotion(snapshot.eventId(), snapshot.animationKey(), snapshot.startedAtUnixMs(), merged, snapshot.expressions(), snapshot.controllers()));
    }
    public synchronized void expression(String text, List<Float> values, long now) {
        List<CloudPlayerMotion.Expression> events = new ArrayList<>(snapshot.expressions());
        events.removeIf(event -> now - event.startedAtUnixMs() > 30000);
        if (events.size() == 16) events.remove(0);
        events.add(new CloudPlayerMotion.Expression(UUID.randomUUID().toString(), now, text, values));
        replaceSnapshot(new CloudPlayerMotion(snapshot.eventId(), snapshot.animationKey(), snapshot.startedAtUnixMs(), snapshot.roaming(), events, snapshot.controllers()));
    }
    public synchronized void controller(String key, String state, long started, Map<String, Float> variables) {
        var next = new CloudPlayerMotion.Controller(state, started, variables);
        if (next.equals(snapshot.controllers().get(key))) return;
        Map<String, CloudPlayerMotion.Controller> controllers = new LinkedHashMap<>();
        // All controllers share the same scoped storage. Keep their variables coherent without changing their clocks.
        snapshot.controllers().forEach((name, old) -> controllers.put(name,
                new CloudPlayerMotion.Controller(old.state(), old.startedAtUnixMs(), next.variables())));
        controllers.put(key, next);
        var candidate = new CloudPlayerMotion(snapshot.eventId(), snapshot.animationKey(), snapshot.startedAtUnixMs(), snapshot.roaming(), snapshot.expressions(), controllers);
        candidate.toJson();
        snapshot = candidate;
    }
    public synchronized void clear() { snapshot = new CloudPlayerMotion(UUID.randomUUID().toString(), "", 0, Map.of(), List.of(), Map.of()); }
}
