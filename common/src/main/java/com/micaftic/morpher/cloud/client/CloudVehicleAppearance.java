package com.micaftic.morpher.cloud.client;

import java.util.*;

/** Explicit binding metadata; never vehicle position, seats or control input. */
public record CloudVehicleAppearance(UUID entityUuid, String worldEpoch, String dimensionId, String entityKind,
        String targetId, long revision, CloudPlayerSelection selection, Map<String,Float> variables,
        long serverTimeMs, long expiresAtMs) {
    public CloudVehicleAppearance {
        Objects.requireNonNull(entityUuid); EntityDisplayContext.slug(worldEpoch); EntityDisplayContext.identifier(dimensionId);
        EntityDisplayContext.identifier(entityKind); EntityDisplayContext.slug(targetId);
        if (revision < 1 || revision >= 9007199254740991L || serverTimeMs < 0 || expiresAtMs < serverTimeMs
                || expiresAtMs - serverTimeMs > 60000) throw new IllegalArgumentException("Invalid vehicle binding lease");
        variables = Map.copyOf(variables);
        if (variables.size() > 256 || variables.entrySet().stream().anyMatch(e -> e.getKey().isBlank()
                || e.getKey().length() > 32 || e.getKey().chars().anyMatch(Character::isISOControl) || !Float.isFinite(e.getValue()))
                || selection == null && !variables.isEmpty()) throw new IllegalArgumentException("Invalid vehicle variables");
        if (selection != null) selection = selection.withoutMotion();
    }
    public long deadlineNanos(long receipt) { return receipt + (expiresAtMs-serverTimeMs) * 1000000L; }
    public boolean sameBinding(CloudVehicleAppearance other) {
        return entityUuid.equals(other.entityUuid) && worldEpoch.equals(other.worldEpoch)
            && dimensionId.equals(other.dimensionId) && entityKind.equals(other.entityKind)
            && targetId.equals(other.targetId) && revision == other.revision
            && Objects.equals(selection, other.selection) && variables.equals(other.variables);
    }
}
