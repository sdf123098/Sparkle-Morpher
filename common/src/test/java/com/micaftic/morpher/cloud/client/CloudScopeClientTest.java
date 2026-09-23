package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudScopeClientTest {
    @Test
    void parsesScopeCatalogAndKeepsScopeFieldsSeparate() {
        var scopes = CloudScopeClient.parseScopesForTest("[{\"scope_id\":\"scope-1\",\"tenant_id\":\"tenant\",\"name\":\"World\",\"world_epoch\":\"epoch-1\",\"offline_policy\":\"CLAIM_CODE\"}]");
        assertEquals("scope-1", scopes.getFirst().scopeId());
        assertEquals("epoch-1", scopes.getFirst().worldEpoch());
        assertEquals("CLAIM_CODE", scopes.getFirst().offlinePolicy());
    }

    @Test
    void rejectsPathTraversalSegments() {
        assertThrows(IllegalArgumentException.class, () -> CloudScopeClient.segment("../targets"));
    }

    @Test
    void parsesDurableEventRecoveryCursorAndPayload() {
        var recovery = CloudScopeClient.parseRecoveryForTest("{" +
                "\"scope_id\":\"scope-1\",\"from_cursor\":3,\"to_cursor\":4,\"has_more\":false," +
                "\"entries\":[{" +
                "\"sequence\":4,\"event_id\":\"event-1\",\"kind\":\"APPEARANCE_UPDATED\"," +
                "\"payload\":{\"target_id\":\"target\",\"revision\":2,\"texture_id\":\"tex\",\"disabled\":false}" +
                "}]} ");

        assertEquals(4, recovery.toCursor());
        assertEquals("event-1", recovery.events().getFirst().eventId());
        assertEquals(2, recovery.events().getFirst().appearance().revision());
    }

    @Test
    void parsesAnimationSnapshotsFromRecoveryEvents() {
        long expiresAt = System.currentTimeMillis() + 5_000;
        var recovery = CloudScopeClient.parseRecoveryForTest("{" +
                "\"from_cursor\":7,\"to_cursor\":8,\"has_more\":false," +
                "\"entries\":[{" +
                "\"sequence\":8,\"event_id\":\"animation-1\",\"kind\":\"AnimationState\"," +
                "\"payload\":{\"target_id\":\"target\",\"revision\":4,\"channel\":\"body\",\"action\":\"PLAY\",\"animation_key\":\"run\",\"expires_at_unix_ms\":" + expiresAt + "}" +
                "}]} ");

        var animation = recovery.events().getFirst().animation();
        assertEquals("target", animation.targetId());
        assertEquals(4, animation.revision());
        assertEquals("body", animation.channel());
        assertEquals("run", animation.animationKey());
        assertEquals(expiresAt, animation.expiresAtUnixMs());
    }

    @Test
    void parsesEntityBindingObservationState() {
        var bindings = CloudScopeClient.parseBindingsForTest("[{\"binding_id\":\"binding-1\",\"scope_id\":\"scope-1\",\"world_epoch\":\"epoch-1\",\"entity_uuid\":\"12345678-1234-1234-1234-1234567890ab\",\"entity_kind\":\"PLAYER\",\"target_id\":\"target-1\",\"observation_state\":\"ACTIVE\",\"last_seen_at\":\"2026-09-22T00:00:00Z\",\"revision\":2}]");
        assertEquals("ACTIVE", bindings.getFirst().observationState());
        assertEquals(2, bindings.getFirst().revision());
    }

    @Test
    void parsesTargetAndAclManagementResponses() {
        var target = CloudScopeClient.parseTargetForTest("{\"target_id\":\"target-1\",\"scope_id\":\"scope-1\",\"kind\":\"PLAYER\",\"display_name\":\"Player\",\"revision\":3}");
        var acl = CloudScopeClient.parseAclForTest("[{\"account_id\":\"account-1\",\"role\":\"editor\"}]");
        assertEquals("PLAYER", target.kind());
        assertEquals("editor", acl.getFirst().role());
    }

    @Test
    void validatesManagementRequestText() {
        assertThrows(IllegalArgumentException.class, () -> CloudScopeClient.segment("scope\n"));
    }
}
