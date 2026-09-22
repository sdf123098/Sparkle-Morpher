package com.micaftic.morpher.cloud.scope;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable Cloud scope reference; address aliases are deliberately excluded. */
public record CloudScopeRef(String instanceId, String tenantId, String scopeId, String worldEpoch) {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    public CloudScopeRef {
        instanceId = requireSegment(instanceId, "instanceId");
        tenantId = requireSegment(tenantId, "tenantId");
        scopeId = requireSegment(scopeId, "scopeId");
        worldEpoch = requireSegment(worldEpoch, "worldEpoch");
    }

    public String toWireString() {
        return instanceId + ":" + tenantId + ":" + scopeId + ":" + worldEpoch;
    }

    private static String requireSegment(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (!SEGMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a bounded Cloud identifier");
        }
        return normalized;
    }
}
