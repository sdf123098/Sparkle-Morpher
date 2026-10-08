package com.micaftic.morpher.cloud.client;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Bounded render metadata; called only on the client thread, with native owner observations. */
public final class ProjectileSnapshotLedger {
    public record Observation(String entityKind, UUID ownerUuid) {
        public Observation { EntityDisplayContext.identifier(entityKind); }
    }
    public record Lease(ProjectileAppearanceSnapshot snapshot, long revision, long expiresAtNanos) {}
    private final int capacity;
    private final Map<UUID, Observation> observed = new HashMap<>();
    private final LinkedHashMap<UUID, Lease> leases = new LinkedHashMap<>();
    private EntityDisplayContext context;
    public ProjectileSnapshotLedger(int capacity) {
        if (capacity < 1 || capacity > 65536) throw new IllegalArgumentException("Invalid ledger capacity");
        this.capacity = capacity;
    }
    public void enter(EntityDisplayContext next) {
        if (!Objects.equals(context, next)) { context = next; observed.clear(); leases.clear(); }
    }
    public void observe(UUID entity, Observation value) {
        Objects.requireNonNull(entity); Objects.requireNonNull(value);
        if (!observed.containsKey(entity) && observed.size() >= capacity) return;
        observed.put(entity, value);
        Lease existing = leases.get(entity);
        if (existing != null && !matches(value, existing.snapshot())) leases.remove(entity);
    }
    public void unload(UUID entity) { observed.remove(entity); leases.remove(entity); }
    public void retain(java.util.Set<UUID> loaded) { observed.keySet().retainAll(loaded); leases.keySet().retainAll(loaded); }
    public boolean accept(EntityDisplayContext requestContext, ProjectileAppearanceSnapshot value,
            long revision, long expiresAtNanos, long nowNanos) {
        if (!Objects.equals(context, requestContext) || context == null || revision < 1
                || expiresAtNanos <= nowNanos || !value.worldEpoch().equals(context.worldEpoch())
                || !value.dimensionId().equals(context.dimensionId())
                || !value.selection().instanceId().equals(context.instanceId())
                || !value.selection().originSha256().equals(context.originSha256())
                || !matches(observed.get(value.entityUuid()), value)) return false;
        Lease existing = leases.get(value.entityUuid());
        if (existing != null && (revision < existing.revision()
                || !existing.snapshot().equals(value)
                || revision == existing.revision() && expiresAtNanos > existing.expiresAtNanos())) return false;
        if (existing == null && leases.size() >= capacity) return false;
        leases.put(value.entityUuid(), new Lease(value, revision, expiresAtNanos));
        return true;
    }
    public Lease get(UUID entity, long nowNanos) {
        Lease lease = leases.get(entity);
        if (lease != null && lease.expiresAtNanos() <= nowNanos) { leases.remove(entity); return null; }
        return lease;
    }
    public int size() { return leases.size(); }
    private static boolean matches(Observation observed, ProjectileAppearanceSnapshot snapshot) {
        return observed != null && observed.ownerUuid() != null
                && observed.ownerUuid().equals(snapshot.sourceEntityUuid())
                && observed.entityKind().equals(snapshot.entityKind());
    }
}
