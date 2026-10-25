package com.micaftic.morpher.client.projectile;

import com.micaftic.morpher.cloud.client.CloudPlayerSelection;
import com.micaftic.morpher.cloud.client.EntityDisplayContext;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable local appearance frozen at firing time; independent of later network lifecycles. */
public record ProjectileDisplaySnapshot(UUID entityUuid, UUID ownerUuid, String entityKind,
        ProjectileModelRef model, Map<String, Float> variables, String firingItemId,
        String eventId, CloudPlayerSelection selection, String identityId,
        long worldGeneration, long observedAtNanos) {
    public ProjectileDisplaySnapshot {
        Objects.requireNonNull(entityUuid);
        Objects.requireNonNull(ownerUuid);
        EntityDisplayContext.identifier(entityKind);
        Objects.requireNonNull(model);
        variables = Map.copyOf(variables);
        if (variables.size() > 32 || variables.entrySet().stream().anyMatch(entry -> entry.getKey().isBlank()
                || entry.getKey().length() > 32 || entry.getKey().chars().anyMatch(Character::isISOControl)
                || !Float.isFinite(entry.getValue()))) {
            throw new IllegalArgumentException("Invalid projectile display variables");
        }
        if (firingItemId != null) {
            EntityDisplayContext.identifier(firingItemId);
        }
        EntityDisplayContext.slug(eventId);
        if (selection != null) {
            selection = selection.withoutMotion();
        }
        if (identityId != null) {
            EntityDisplayContext.slug(identityId);
        }
        if (worldGeneration < 0 || observedAtNanos < 0) {
            throw new IllegalArgumentException("Invalid projectile display lifetime");
        }
    }
}
