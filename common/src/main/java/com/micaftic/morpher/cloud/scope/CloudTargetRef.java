package com.micaftic.morpher.cloud.scope;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable Cloud target reference; display names are intentionally absent. */
public record CloudTargetRef(String instanceId, String tenantId, String targetId) {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    public CloudTargetRef {
        instanceId = requireSegment(instanceId, "instanceId");
        tenantId = requireSegment(tenantId, "tenantId");
        targetId = requireSegment(targetId, "targetId");
    }

    public String toWireString() {
        return instanceId + ":" + tenantId + ":" + targetId;
    }

    private static String requireSegment(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (!SEGMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a bounded Cloud identifier");
        }
        return normalized;
    }
}
