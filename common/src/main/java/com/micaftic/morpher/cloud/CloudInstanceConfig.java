package com.micaftic.morpher.cloud;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * User-selected Cloud instance endpoint.
 *
 * <p>The endpoint is an instance origin, not an identity provider URL and not
 * an object-storage URL.  Provider discovery remains server-controlled.</p>
 */
public record CloudInstanceConfig(String instanceId, URI origin, String protocolVersion) {

    public static final String PROTOCOL_V1 = "spm.cloud.v1";
    private static final Pattern INSTANCE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public CloudInstanceConfig {
        instanceId = normalizeInstanceId(instanceId);
        origin = normalizeOrigin(origin);
        protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion").trim();
        if (!PROTOCOL_V1.equals(protocolVersion)) {
            throw new IllegalArgumentException("Unsupported Cloud protocol: " + protocolVersion);
        }
    }

    public static CloudInstanceConfig v1(String instanceId, URI origin) {
        return new CloudInstanceConfig(instanceId, origin, PROTOCOL_V1);
    }

    public URI websocketUri() {
        return URI.create("wss://" + origin.getRawAuthority() + "/v1/realtime");
    }

    public URI apiUri(String path) {
        Objects.requireNonNull(path, "path");
        if (!path.startsWith("/v1/") || path.contains("..")) {
            throw new IllegalArgumentException("Cloud API path must stay below /v1/: " + path);
        }
        return origin.resolve(path);
    }

    private static String normalizeInstanceId(String value) {
        String normalized = Objects.requireNonNull(value, "instanceId").trim().toLowerCase(Locale.ROOT);
        if (!INSTANCE_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("instanceId must be a lowercase ASCII Cloud slug");
        }
        return normalized;
    }

    private static URI normalizeOrigin(URI value) {
        URI origin = Objects.requireNonNull(value, "origin");
        if (!"https".equalsIgnoreCase(origin.getScheme())
                || origin.getRawAuthority() == null
                || origin.getUserInfo() != null
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null
                || (origin.getRawPath() != null && !origin.getRawPath().isBlank() && !"/".equals(origin.getRawPath()))) {
            throw new IllegalArgumentException("Cloud origin must be a bare HTTPS origin without credentials or path");
        }
        return URI.create("https://" + origin.getRawAuthority());
    }
}
