package com.micaftic.morpher.cloud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CloudAuthClientTest {

    @Test
    void parsesSessionWithoutPersistingOrTransformingTokens() {
        CloudSession session = CloudAuthClient.parseSession("""
                {"access_token":"access-secret","refresh_token":"refresh-secret",
                 "access_expires_in_seconds":900,"refresh_expires_in_seconds":2592000}
                """);
        assertEquals("access-secret", session.accessToken());
        assertEquals("refresh-secret", session.refreshToken());
        assertEquals(900, session.accessExpiresInSeconds());
    }

    @Test
    void rejectsEmptyTokens() {
        assertThrows(IllegalArgumentException.class, () -> new CloudSession("", "refresh", 900, 2592000));
    }
}
