package com.micaftic.morpher.cloud.client;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resolves Cloud bindings against the current client scope/world generation.
 *
 * <p>This class deliberately has no Minecraft dependency. A loader adapter
 * supplies an observed entity UUID/kind and consumes the result on the client
 * thread. Seeing an entity never creates or claims a binding.</p>
 */
public final class CloudEntityBindingResolver {
    private final AtomicLong generation = new AtomicLong();
    private final Map<UUID, CloudScopeClient.CloudEntityBinding> bindings = new ConcurrentHashMap<>();
    private volatile Context context;

    public CloudEntityBindingResolver() {
        context = new Context("", "", 0L);
    }

    public synchronized void replace(String scopeId, String worldEpoch, List<CloudScopeClient.CloudEntityBinding> entries) {
        requireText(scopeId, "scopeId");
        requireText(worldEpoch, "worldEpoch");
        Objects.requireNonNull(entries, "entries");
        Map<UUID, CloudScopeClient.CloudEntityBinding> next = new ConcurrentHashMap<>();
        for (CloudScopeClient.CloudEntityBinding entry : entries) {
            Objects.requireNonNull(entry, "binding entry");
            if (!scopeId.equals(entry.scopeId()) || !worldEpoch.equals(entry.worldEpoch())) {
                throw new IllegalArgumentException("Cloud binding belongs to a different scope/world epoch");
            }
            UUID uuid = parseUuid(entry.entityUuid());
            CloudScopeClient.CloudEntityBinding previous = next.put(uuid, entry);
            if (previous != null && !previous.bindingId().equals(entry.bindingId())) {
                throw new IllegalArgumentException("Cloud binding catalog contains duplicate entity UUID");
            }
        }
        bindings.clear();
        bindings.putAll(next);
        context = new Context(scopeId, worldEpoch, generation.incrementAndGet());
    }

    public Resolution resolve(String scopeId, String worldEpoch, UUID entityUuid, String entityKind) {
        Objects.requireNonNull(entityUuid, "entityUuid");
        requireText(entityKind, "entityKind");
        Context current = context;
        if (!current.scopeId().equals(scopeId) || !current.worldEpoch().equals(worldEpoch)) {
            return new Resolution(Status.STALE_CONTEXT, null);
        }
        CloudScopeClient.CloudEntityBinding binding = bindings.get(entityUuid);
        if (binding == null) return new Resolution(Status.UNBOUND, null);
        if (!binding.entityKind().equals(entityKind)) return new Resolution(Status.KIND_MISMATCH, binding);
        return new Resolution(Status.BOUND, binding);
    }

    public Context context() {
        return context;
    }

    public void clear() {
        bindings.clear();
        context = new Context("", "", generation.incrementAndGet());
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(requireText(value, "entityUuid"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Cloud binding entityUuid must be a canonical UUID", failure);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    public enum Status {
        BOUND,
        UNBOUND,
        KIND_MISMATCH,
        STALE_CONTEXT
    }

    public record Context(String scopeId, String worldEpoch, long generation) {
    }

    public record Resolution(Status status, CloudScopeClient.CloudEntityBinding binding) {
        public Resolution {
            Objects.requireNonNull(status, "status");
            if (status == Status.BOUND && binding == null) throw new IllegalArgumentException("BOUND resolution requires a binding");
        }
    }
}
