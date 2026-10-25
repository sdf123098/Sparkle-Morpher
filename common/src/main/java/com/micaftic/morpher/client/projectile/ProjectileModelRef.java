package com.micaftic.morpher.client.projectile;

import com.micaftic.morpher.cloud.client.EntityDisplayContext;

/** The exact model and projectile bundle selected at firing time. */
public record ProjectileModelRef(String runtimeModelId, String bundleKey) {
    public ProjectileModelRef {
        if (runtimeModelId == null || runtimeModelId.isBlank() || runtimeModelId.length() > 256) {
            throw new IllegalArgumentException("Invalid projectile runtime model id");
        }
        EntityDisplayContext.identifier(bundleKey);
    }
}
