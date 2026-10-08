package com.micaftic.morpher.cloud.client;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable firing appearance. Motion and collision remain native entity inputs. */
public record ProjectileAppearanceSnapshot(String worldEpoch, String dimensionId, UUID entityUuid,
        String entityKind, String sourceIdentityId, UUID sourceEntityUuid, String eventId,
        CloudPlayerSelection selection, String projectileBundleKey, Map<String, Float> variables,
        String firingItemId) {
    public ProjectileAppearanceSnapshot {
        EntityDisplayContext.slug(worldEpoch); EntityDisplayContext.identifier(dimensionId);
        Objects.requireNonNull(entityUuid); Objects.requireNonNull(sourceEntityUuid);
        EntityDisplayContext.identifier(entityKind); EntityDisplayContext.slug(sourceIdentityId);
        EntityDisplayContext.slug(eventId); Objects.requireNonNull(selection);
        selection = selection.withoutMotion();
        EntityDisplayContext.identifier(projectileBundleKey);
        if (firingItemId != null) EntityDisplayContext.identifier(firingItemId);
        variables = Map.copyOf(variables);
        if (variables.size() > 256 || variables.entrySet().stream().anyMatch(e -> e.getKey().isEmpty()
                || e.getKey().length() > 32 || e.getKey().chars().anyMatch(Character::isISOControl)
                || !Float.isFinite(e.getValue()))) throw new IllegalArgumentException("Invalid firing variables");
    }
}
