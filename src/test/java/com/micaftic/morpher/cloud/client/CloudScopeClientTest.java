package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudScopeClientTest {
    @Test
    void parsesScopeCatalogAndKeepsScopeFieldsSeparate() {
        var scopes = CloudScopeClient.parseScopesForTest("[{\"scope_id\":\"scope-1\",\"tenant_id\":\"tenant\",\"name\":\"World\",\"world_epoch\":\"epoch-1\"}]");
        assertEquals("scope-1", scopes.getFirst().scopeId());
        assertEquals("epoch-1", scopes.getFirst().worldEpoch());
    }

    @Test
    void rejectsPathTraversalSegments() {
        assertThrows(IllegalArgumentException.class, () -> CloudScopeClient.segment("../targets"));
    }

    @Test
    void parsesDurableEventRecoveryCursorAndPayload() {
        var recovery = CloudScopeClient.parseRecoveryForTest("{\"scope_id\":\"scope-1\",\"from_cursor\":3,\"to_cursor\":4,\"has_more\":false,\"entries\":[{\"sequence\":4,\"event_id\":\"event-1\",\"kind\":\"APPEARANCE_UPDATED\",\"payload\":{\"target_id\":\"target\",\"revision\":2,\"texture_id\":\"tex\",\"disabled\":false}}]}");
        assertEquals(4, recovery.toCursor());
        assertEquals("event-1", recovery.events().getFirst().eventId());
        assertEquals(2, recovery.events().getFirst().appearance().revision());
    }
}
