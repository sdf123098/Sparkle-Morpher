package com.micaftic.morpher.cloud.client;

/** In-memory Cloud session; tokens must never be persisted in model metadata or logs. */
public record CloudSession(
        String accessToken,
        String refreshToken,
        long accessExpiresInSeconds,
        long refreshExpiresInSeconds
) {
    public CloudSession {
        if (accessToken == null || accessToken.isBlank() || refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Cloud session tokens must be non-blank");
        }
        if (accessExpiresInSeconds <= 0 || refreshExpiresInSeconds <= 0) {
            throw new IllegalArgumentException("Cloud session expirations must be positive");
        }
    }
}

