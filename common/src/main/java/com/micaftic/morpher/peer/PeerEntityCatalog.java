package com.micaftic.morpher.peer;

import java.util.*;

/** Bounded entity metadata; actual Minecraft ownership is checked by each observing client. */
public final class PeerEntityCatalog {
    public static final int PER_OWNER = 16, HISTORY_PER_OWNER = 128, PAGE_SIZE = 24;
    private record Key(PeerSession owner, UUID entity) {}
    public record Page(int next, List<PeerEntityAppearance> entries) { public Page { entries = List.copyOf(entries); } }
    private final Map<Key, PeerEntityAppearance> entries = new LinkedHashMap<>();
    private final Map<Key, Long> revisions = new HashMap<>();
    public synchronized boolean publish(PeerEntityAppearance entry) {
        Key key = new Key(entry.owner(), entry.entityId());
        if (entry.revision() <= revisions.getOrDefault(key, 0L)) return false;
        if (!revisions.containsKey(key) && revisions.keySet().stream().filter(k -> k.owner.equals(entry.owner())).count() >= HISTORY_PER_OWNER)
            throw new IllegalStateException("Entity history limit reached");
        if (!entry.removed() && !entries.containsKey(key) && entries.keySet().stream().filter(k -> k.owner.equals(entry.owner())).count() >= PER_OWNER)
            throw new IllegalStateException("Entity sharing limit reached");
        revisions.put(key, entry.revision());
        if (entry.removed()) entries.remove(key); else entries.put(key, entry);
        return true;
    }
    public synchronized PeerEntityAppearance get(PeerSession owner, UUID entity) { return entries.get(new Key(owner, entity)); }
    public synchronized boolean owns(PeerSession owner, PeerAppearance appearance) {
        return entries.values().stream().anyMatch(e -> e.owner().equals(owner) && e.model() != null && e.assetAppearance().equals(appearance));
    }
    public synchronized boolean ownsModel(PeerSession owner, PeerModelIdentity model) {
        return entries.values().stream().anyMatch(e -> e.owner().equals(owner) && Objects.equals(e.model(), model));
    }
    public synchronized Page page(int cursor) {
        List<PeerEntityAppearance> all = List.copyOf(entries.values());
        if (cursor < 0 || cursor > PeerLimits.CAPACITY_CEILING * PER_OWNER) throw new IllegalArgumentException("Invalid entity cursor");
        int start = Math.min(cursor, all.size()), end = Math.min(start + PAGE_SIZE, all.size());
        return new Page(end < all.size() ? end : -1, all.subList(start, end));
    }
    public synchronized void leave(PeerSession owner) {
        entries.keySet().removeIf(k -> k.owner.equals(owner)); revisions.keySet().removeIf(k -> k.owner.equals(owner));
    }
    public synchronized void clear() { entries.clear(); revisions.clear(); }
}
