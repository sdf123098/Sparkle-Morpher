package com.micaftic.morpher.cloud.client;

import java.util.Objects;

/** Complete client-side boundary for an asynchronous entity display response. */
public record EntityDisplayContext(String instanceId, String originSha256, String scopeId,
        String worldEpoch, String dimensionId, long generation) {
    public EntityDisplayContext {
        slug(instanceId); slug(scopeId); slug(worldEpoch);
        if (originSha256 == null || !originSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid instance origin");
        identifier(dimensionId);
        if (generation < 0) throw new IllegalArgumentException("Invalid world generation");
    }
    static String slug(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Invalid display identifier");
        return value;
    }
    static String identifier(String value) {
        Objects.requireNonNull(value);
        if (value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid resource identifier");
        return value;
    }
}
