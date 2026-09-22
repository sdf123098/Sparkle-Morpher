package com.micaftic.morpher.cloud.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Namespaced game identity used by SPM Cloud.
 *
 * <p>The UUID remains a normal UUID value.  The namespace is serialized only
 * at the Cloud protocol boundary, so an Offline identity can never be
 * mistaken for an Official or Yggdrasil identity.</p>
 */
public record CloudIdentityRef(Kind kind, String providerId, String scopeId, UUID profileUuid) {

    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public CloudIdentityRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(profileUuid, "profileUuid");
        providerId = normalizeOptional(providerId, "providerId");
        scopeId = normalizeOptional(scopeId, "scopeId");
        switch (kind) {
            case OFFICIAL -> {
                requireAbsent(providerId, "Official identity must not carry a provider");
                requireAbsent(scopeId, "Official identity must not carry a scope");
            }
            case YGGDRASIL -> {
                requirePresent(providerId, "Yggdrasil identity requires a provider");
                requireAbsent(scopeId, "Yggdrasil identity must not carry a scope");
            }
            case OFFLINE -> {
                requireAbsent(providerId, "Offline identity must not carry a provider");
                requirePresent(scopeId, "Offline identity requires a scope");
            }
        }
    }

    public static CloudIdentityRef official(UUID profileUuid) {
        return new CloudIdentityRef(Kind.OFFICIAL, null, null, profileUuid);
    }

    public static CloudIdentityRef yggdrasil(String providerId, UUID profileUuid) {
        return new CloudIdentityRef(Kind.YGGDRASIL, providerId, null, profileUuid);
    }

    public static CloudIdentityRef offline(String scopeId, UUID profileUuid) {
        return new CloudIdentityRef(Kind.OFFLINE, null, scopeId, profileUuid);
    }

    public static CloudIdentityRef parse(String wireValue) {
        Objects.requireNonNull(wireValue, "wireValue");
        String[] parts = wireValue.split(":", -1);
        try {
            return switch (parts[0]) {
                case "official" -> parts.length == 2
                        ? official(UUID.fromString(parts[1]))
                        : invalid(wireValue);
                case "yggdrasil" -> parts.length == 3
                        ? yggdrasil(parts[1], UUID.fromString(parts[2]))
                        : invalid(wireValue);
                case "offline" -> parts.length == 3
                        ? offline(parts[1], UUID.fromString(parts[2]))
                        : invalid(wireValue);
                default -> invalid(wireValue);
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Cloud identity reference: " + wireValue, e);
        }
    }

    public String toWireString() {
        String uuid = profileUuid.toString();
        return switch (kind) {
            case OFFICIAL -> "official:" + uuid;
            case YGGDRASIL -> "yggdrasil:" + providerId + ":" + uuid;
            case OFFLINE -> "offline:" + scopeId + ":" + uuid;
        };
    }

    private static String normalizeOptional(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SEGMENT.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase ASCII Cloud slug");
        }
        return normalized;
    }

    private static void requirePresent(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireAbsent(String value, String message) {
        if (value != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static CloudIdentityRef invalid(String wireValue) {
        throw new IllegalArgumentException("Invalid Cloud identity reference: " + wireValue);
    }

    public enum Kind {
        OFFICIAL,
        YGGDRASIL,
        OFFLINE
    }
}
