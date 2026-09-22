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
}
